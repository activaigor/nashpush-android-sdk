package com.nashpush.sdk.rest

import android.net.TrafficStats
import android.os.Build
import android.os.Looper
import com.nashpush.sdk.Nashpush
import com.nashpush.sdk.Nashpush.loger
import com.nashpush.sdk.rest.NashpushThrowable.NashPushMainThreadException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.util.*
import javax.net.ssl.HttpsURLConnection

internal object NashpushRestClient {
    private const val API_VERSION = "v1"
    private const val BASE_URL = "https://gateway.production.almightypush.com/api/$API_VERSION/"
    private const val STATISTIC_URL =
        "https://callbacks-api.production.almightypush.com/api/$API_VERSION/"
    private const val THREAD_ID = 10000
    private const val TIMEOUT = 120000
    private const val GET_TIMEOUT = 60000
    private var channelToken = ""
    fun setChannelToken(token: String) {
        loger(Nashpush.LOG_LVL.VERBOSE, "Channel-Token: $token")
        channelToken = token
    }

    private fun getThreadTimeout(timeout: Int): Int {
        return timeout + 5000
    }

    fun post(url: String, jsonBody: JSONObject?, responseHandler: ResponseHandler) {
        Thread(
            { makeRequest(BASE_URL + url, "POST", jsonBody, responseHandler, TIMEOUT) },
            "REST_POST"
        ).start()
    }

    fun postStatistic(url: String, jsonBody: JSONObject?, responseHandler: ResponseHandler) {
        Thread(
            { makeRequest(STATISTIC_URL + url, "POST", jsonBody, responseHandler, TIMEOUT) },
            "REST_POST"
        ).start()
    }

    operator fun get(url: String, responseHandler: ResponseHandler) {
        Thread(
            { makeRequest(BASE_URL + url, null, null, responseHandler, GET_TIMEOUT) },
            "REST_GET"
        ).start()
    }

    private fun makeRequest(
        url: String,
        method: String?,
        jsonBody: JSONObject?,
        responseHandler: ResponseHandler,
        timeout: Int
    ) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) throw NashPushMainThreadException("Method: $method was called from the Main Thread!")
        val callbackThread = arrayOfNulls<Thread>(1)
        val connectionThread = Thread({
            callbackThread[0] = startHTTPConnection(url, method, jsonBody, responseHandler, timeout)
        }, "HTTPConnection")
        connectionThread.start()

        // getResponseCode() can hang past it's timeout setting so join it's thread to ensure it is timing out.
        try {
            // Sequentially wait for connectionThread to execute
            connectionThread.join(getThreadTimeout(timeout).toLong())
            if (connectionThread.state != Thread.State.TERMINATED) connectionThread.interrupt()
            if (callbackThread[0] != null) callbackThread[0]!!.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun startHTTPConnection(
        url: String,
        method: String?,
        jsonBody: JSONObject?,
        responseHandler: ResponseHandler,
        timeout: Int
    ): Thread? {
        var httpResponse = -1
        var con: HttpURLConnection? = null
        var callbackThread: Thread?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TrafficStats.setThreadStatsTag(THREAD_ID)
        }
        try {
            loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: Making request to: $url")
            loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: with data: ${jsonBody.toString()}")
            con = newHttpURLConnection(url)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 && con is HttpsURLConnection) {
                val conHttps = con
                conHttps.sslSocketFactory = TLS12SocketFactory(conHttps.sslSocketFactory)
            }
            con.useCaches = false
            con.connectTimeout = timeout
            con.readTimeout = timeout
            con.setRequestProperty("Accept", "*/*")
            con.setRequestProperty("Channel-Token", channelToken)
            if (jsonBody != null) con.doInput = true
            if (method != null) {
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                con.requestMethod = method
                con.doOutput = true
            }
            if (jsonBody != null) {
                loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: $method SEND JSON: $jsonBody")
                val sendBytes = jsonBody.toString().toByteArray(charset("UTF-8"))
                con.setFixedLengthStreamingMode(sendBytes.size)
                val outputStream = con.outputStream
                outputStream.write(sendBytes)
            }
            httpResponse = con.responseCode
            when (httpResponse) {
                HttpURLConnection.HTTP_ACCEPTED, HttpURLConnection.HTTP_OK -> {
                    loger(
                        Nashpush.LOG_LVL.DEBUG,
                        "NashpushRestClient: Successfully finished request to: $url"
                    )
                    val inputStream = con.inputStream
                    val scanner = Scanner(inputStream, "UTF-8")
                    val json = if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                    scanner.close()
                    loger(
                        Nashpush.LOG_LVL.DEBUG,
                        "NashpushRestClient: " + (method ?: "GET") + " RECEIVED JSON: " + json
                    )
                    callbackThread = callResponseHandlerOnSuccess(responseHandler, json)
                }
                else -> {
                    loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: Failed request to: $url")
                    var inputStream = con.errorStream
                    if (inputStream == null) inputStream = con.inputStream
                    var jsonResponse: String? = null
                    if (inputStream != null) {
                        val scanner = Scanner(inputStream, "UTF-8")
                        jsonResponse =
                            if (scanner.useDelimiter("\\A").hasNext()) scanner.next() else ""
                        scanner.close()
                        loger(
                            Nashpush.LOG_LVL.WARN,
                            "NashpushRestClient: $method RECEIVED JSON: $jsonResponse"
                        )
                    } else loger(
                        Nashpush.LOG_LVL.WARN,
                        "NashpushRestClient: $method HTTP Code: $httpResponse No response body!"
                    )
                    callbackThread = callResponseHandlerOnFailure(
                        responseHandler,
                        httpResponse,
                        jsonResponse,
                        null
                    )
                }
            }
        } catch (t: Throwable) {
            if (t is ConnectException || t is UnknownHostException) loger(
                Nashpush.LOG_LVL.INFO,
                "NashpushRestClient: Could not send last request, device is offline. Throwable: " + t.javaClass.name
            ) else loger(
                Nashpush.LOG_LVL.WARN,
                "NashpushRestClient: $method Error thrown from network stack. ",
                t
            )
            callbackThread = callResponseHandlerOnFailure(responseHandler, httpResponse, null, t)
        } finally {
            con?.disconnect()
        }
        return callbackThread
    }

    // These helper methods run the callback a new thread so they don't count towards the fallback thread join timer.
    private fun callResponseHandlerOnSuccess(handler: ResponseHandler?, response: String): Thread? {
        if (handler == null) return null
        val thread = Thread({ handler.onSuccess(response) }, "OS_REST_SUCCESS_CALLBACK")
        thread.start()
        return thread
    }

    private fun callResponseHandlerOnFailure(
        handler: ResponseHandler?,
        statusCode: Int,
        response: String?,
        throwable: Throwable?
    ): Thread? {
        if (handler == null) return null
        val thread = Thread(
            { handler.onFailure(statusCode, response, throwable) },
            "OS_REST_FAILURE_CALLBACK"
        )
        thread.start()
        return thread
    }

    @Throws(IOException::class)
    private fun newHttpURLConnection(url: String): HttpURLConnection {
        return URL(url).openConnection() as HttpURLConnection
    }

    internal abstract class ResponseHandler {
        open fun onSuccess(response: String?) {}
        open fun onFailure(statusCode: Int, response: String?, throwable: Throwable?) {}
    }
}
