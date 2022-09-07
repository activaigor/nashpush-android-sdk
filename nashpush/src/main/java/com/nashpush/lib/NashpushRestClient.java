package com.nashpush.lib;

import static com.nashpush.lib.NashpushUtilsKt.isRunningOnMainThread;

import android.net.TrafficStats;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import javax.net.ssl.HttpsURLConnection;

class NashpushRestClient {
    static abstract class ResponseHandler {
        void onSuccess(String response) {
        }

        void onFailure(int statusCode, String response, Throwable throwable) {
        }
    }

    static final String CACHE_KEY_GET_TAGS = "CACHE_KEY_GET_TAGS";
    static final String CACHE_KEY_REMOTE_PARAMS = "CACHE_KEY_REMOTE_PARAMS";

    private static final String API_VERSION = "v1";
    private static final String BASE_URL = "https://gateway.staging.almightypush.com/api/" + API_VERSION + "/";
    //   private static final String BASE_URL = "https://webpush.staging.almightypush.com/api/v1/firebase-credentials/";
    private static final String STATISTIC_URL = "https://callbacks-api.staging.almightypush.com/api/" + API_VERSION + "/";

    private static final int THREAD_ID = 10000;
    private static final int TIMEOUT = 120_000;
    private static final int GET_TIMEOUT = 60_000;
    private static String channelToken = "";

    public static void setChannelToken(String token) {
        channelToken = token;
    }

    private static int getThreadTimeout(int timeout) {
        return timeout + 5_000;
    }

    public static void put(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {
        new Thread(new Runnable() {
            public void run() {
                makeRequest(url, "PUT", jsonBody, responseHandler, TIMEOUT);
            }
        }, "REST_PUT").start();
    }

    public static void post(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {
        new Thread(new Runnable() {
            public void run() {
                makeRequest(BASE_URL + url, "POST", jsonBody, responseHandler, TIMEOUT);
            }
        }, "REST_POST").start();
    }

    public static void postStatistic(final String url, final JSONObject jsonBody, final ResponseHandler responseHandler) {
        new Thread(new Runnable() {
            public void run() {
                makeRequest(STATISTIC_URL + url, "POST", jsonBody, responseHandler, TIMEOUT);
            }
        }, "REST_POST").start();
    }

    public static void get(final String url, final ResponseHandler responseHandler) {
        new Thread(new Runnable() {
            public void run() {
                makeRequest(BASE_URL + url, null, null, responseHandler, GET_TIMEOUT);
            }
        }, "REST_GET").start();
    }

    public static void getSync(final String url, final ResponseHandler responseHandler) {
        makeRequest(url, null, null, responseHandler, GET_TIMEOUT);
    }

    public static void putSync(String url, JSONObject jsonBody, ResponseHandler responseHandler) {
        makeRequest(url, "PUT", jsonBody, responseHandler, TIMEOUT);
    }

    public static void postSync(String url, JSONObject jsonBody, ResponseHandler responseHandler) {
        makeRequest(url, "POST", jsonBody, responseHandler, TIMEOUT);
    }

    private static void makeRequest(final String url, final String method, final JSONObject jsonBody, final ResponseHandler responseHandler, final int timeout) {
        if (isRunningOnMainThread())
            throw new NashpushThrowable.NashPushMainThreadException("Method: " + method + " was called from the Main Thread!");

        final Thread[] callbackThread = new Thread[1];
        Thread connectionThread = new Thread(new Runnable() {
            public void run() {
                callbackThread[0] = startHTTPConnection(url, method, jsonBody, responseHandler, timeout);
            }
        }, "HTTPConnection");

        connectionThread.start();

        // getResponseCode() can hang past it's timeout setting so join it's thread to ensure it is timing out.
        try {
            // Sequentially wait for connectionThread to execute
            connectionThread.join(getThreadTimeout(timeout));
            if (connectionThread.getState() != Thread.State.TERMINATED)
                connectionThread.interrupt();
            if (callbackThread[0] != null)
                callbackThread[0].join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static Thread startHTTPConnection(String url, String method, JSONObject jsonBody, ResponseHandler responseHandler, int timeout) {
        int httpResponse = -1;
        HttpURLConnection con = null;
        Thread callbackThread;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TrafficStats.setThreadStatsTag(THREAD_ID);
        }

        try {
            Nashpush.Loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: Making request to: " + url);
            con = newHttpURLConnection(url);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1 && con instanceof HttpsURLConnection) {
                HttpsURLConnection conHttps = (HttpsURLConnection) con;
                conHttps.setSSLSocketFactory(new TLS12SocketFactory(conHttps.getSSLSocketFactory()));
            }

            con.setUseCaches(false);
            con.setConnectTimeout(timeout);
            con.setReadTimeout(timeout);
            con.setRequestProperty("Accept", "*/*");
            con.setRequestProperty("Channel-Token", channelToken);

            if (jsonBody != null)
                con.setDoInput(true);

            if (method != null) {
                con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                con.setRequestMethod(method);
                con.setDoOutput(true);
            }

            if (jsonBody != null) {

                Nashpush.Loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: " + method + " SEND JSON: " + jsonBody);

                byte[] sendBytes = jsonBody.toString().getBytes("UTF-8");
                con.setFixedLengthStreamingMode(sendBytes.length);

                OutputStream outputStream = con.getOutputStream();
                outputStream.write(sendBytes);
            }

            httpResponse = con.getResponseCode();

            Nashpush.Loger(Nashpush.LOG_LVL.VERBOSE, "NashpushRestClient: After con.getResponseCode to: " + url);

            switch (httpResponse) {
                case HttpURLConnection.HTTP_ACCEPTED:
                case HttpURLConnection.HTTP_OK: // 200
                    Nashpush.Loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: Successfully finished request to: " + url);

                    InputStream inputStream = con.getInputStream();
                    Scanner scanner = new Scanner(inputStream, "UTF-8");
                    String json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                    scanner.close();
                    Nashpush.Loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: " + (method == null ? "GET" : method) + " RECEIVED JSON: " + json);

                    callbackThread = callResponseHandlerOnSuccess(responseHandler, json);
                    break;
                default: // Request failed
                    Nashpush.Loger(Nashpush.LOG_LVL.DEBUG, "NashpushRestClient: Failed request to: " + url);
                    inputStream = con.getErrorStream();
                    if (inputStream == null)
                        inputStream = con.getInputStream();

                    String jsonResponse = null;
                    if (inputStream != null) {
                        scanner = new Scanner(inputStream, "UTF-8");
                        jsonResponse = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                        scanner.close();
                        Nashpush.Loger(Nashpush.LOG_LVL.WARN, "NashpushRestClient: " + method + " RECEIVED JSON: " + jsonResponse);
                    } else
                        Nashpush.Loger(Nashpush.LOG_LVL.WARN, "NashpushRestClient: " + method + " HTTP Code: " + httpResponse + " No response body!");

                    callbackThread = callResponseHandlerOnFailure(responseHandler, httpResponse, jsonResponse, null);
            }
        } catch (Throwable t) {
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException)
                Nashpush.Loger(Nashpush.LOG_LVL.INFO, "NashpushRestClient: Could not send last request, device is offline. Throwable: " + t.getClass().getName());
            else
                Nashpush.Loger(Nashpush.LOG_LVL.WARN, "NashpushRestClient: " + method + " Error thrown from network stack. ", t);

            callbackThread = callResponseHandlerOnFailure(responseHandler, httpResponse, null, t);
        } finally {
            if (con != null)
                con.disconnect();
        }

        return callbackThread;
    }


    // These helper methods run the callback a new thread so they don't count towards the fallback thread join timer.

    private static Thread callResponseHandlerOnSuccess(final ResponseHandler handler, final String response) {
        if (handler == null)
            return null;

        Thread thread = new Thread(new Runnable() {
            public void run() {
                handler.onSuccess(response);
            }
        }, "OS_REST_SUCCESS_CALLBACK");
        thread.start();

        return thread;
    }

    private static Thread callResponseHandlerOnFailure(final ResponseHandler handler, final int statusCode, final String response, final Throwable throwable) {
        if (handler == null)
            return null;

        Thread thread = new Thread(new Runnable() {
            public void run() {
                handler.onFailure(statusCode, response, throwable);
            }
        }, "OS_REST_FAILURE_CALLBACK");
        thread.start();

        return thread;
    }

    private static HttpURLConnection newHttpURLConnection(String url) throws IOException {
        return (HttpURLConnection) new URL(url).openConnection();
    }
}