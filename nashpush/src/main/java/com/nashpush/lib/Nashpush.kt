package com.nashpush.lib

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject

object Nashpush {

    enum class LOG_LVL {
        NONE, FATAL, ERROR, WARN, INFO, DEBUG, VERBOSE
    }

    @JvmStatic
    fun Loger(logLvl: LOG_LVL, message: String) {
        Loger(logLvl, message, null)
    }

    @JvmStatic
    fun Loger(logLvl: LOG_LVL, message: String, throwable: Throwable?) {
        val TAG = "Nashpush"
        if (logLvl.compareTo(logCatLevel) < 1) {
            if (logLvl == LOG_LVL.VERBOSE) Log.v(
                TAG,
                message,
                throwable
            ) else if (logLvl == LOG_LVL.DEBUG) Log.d(
                TAG,
                message,
                throwable
            ) else if (logLvl == LOG_LVL.INFO) Log.i(
                TAG,
                message,
                throwable
            ) else if (logLvl == LOG_LVL.WARN) Log.w(
                TAG,
                message,
                throwable
            ) else if (logLvl == LOG_LVL.ERROR || logLvl == LOG_LVL.FATAL) Log.e(
                TAG,
                message,
                throwable
            )
        }
    }

    private var logCatLevel: LOG_LVL = LOG_LVL.WARN
    private var token = ""

    fun setLogLevel(logLevel: LOG_LVL) {
        logCatLevel = logLevel
    }

    fun initializeApp(channelToken: String, context: Context) {
        NashpushRestClient.setChannelToken(channelToken)
        NashpushRestClient.get(
            "firebase-credentials/",
            object : NashpushRestClient.ResponseHandler() {
                override fun onSuccess(response: String?) {
                    if (response != null) {
                        val body = JSONObject(response)
                        val senderId = body.getString("sender_id")
                        val androidApp = body.getJSONObject("android_app")
                        val projectId = androidApp.getString("project_id")
                        val appId = androidApp.getString("app_id")
                        val apiKey = androidApp.getString("api_key")
                        val configUuid = androidApp.getString("config_uuid")
                        handleDataAndSubscribe(
                            senderId,
                            projectId,
                            appId,
                            apiKey,
                            configUuid,
                            context
                        )
                    }
                }

                override fun onFailure(statusCode: Int, response: String?, throwable: Throwable?) {
                    super.onFailure(statusCode, response, throwable)
                    throwable?.message?.let {
                        Loger(LOG_LVL.ERROR, it)
                    }
                }
            })
    }

    private fun handleDataAndSubscribe(
        senderId: String,
        projectId: String,
        appId: String,
        apiKey: String,
        configUuid: String,
        context: Context
    ) {
        var firebaseMessaging: FirebaseMessaging? = null

        val firebaseOptions = FirebaseOptions.Builder()
            .setGcmSenderId(senderId)
            .setApplicationId(appId)
            .setApiKey(apiKey)
            .setProjectId(projectId)
            .build()

        val app = FirebaseApp.initializeApp(
            context,
            firebaseOptions
        )

        firebaseMessaging = app.get(FirebaseMessaging::class.java) as FirebaseMessaging

        firebaseMessaging.token
            .addOnCompleteListener(object : OnCompleteListener<String?> {
                override fun onComplete(task: Task<String?>) {
                    if (!task.isSuccessful) {
                        Log.w(
                            "TAG",
                            "Fetching FCM registration token failed",
                            task.exception
                        )
                        return
                    }

                    // Get new FCM registration token
                    token = task.result ?: ""
                    Loger(LOG_LVL.VERBOSE, "FirebaseToken: $token")
                    sendTokenToBackend(token, configUuid)
                }
            })

    }

    private fun sendTokenToBackend(token: String, configUuid: String) {
        val jsonObject = JSONObject()
        jsonObject.put("token", token)
        jsonObject.put("firebase_app", configUuid)
        NashpushRestClient.post(
            "subscribers/",
            jsonObject,
            object : NashpushRestClient.ResponseHandler() {
                override fun onSuccess(response: String?) {
                    if (response != null) {
                        val body = JSONObject(response)
                        val uuid = body.getString("uuid")
                        Loger(LOG_LVL.VERBOSE, "uuid: $uuid")
                    }
                }

                override fun onFailure(statusCode: Int, response: String?, throwable: Throwable?) {
                    super.onFailure(statusCode, response, throwable)
                    throwable?.message?.let {
                        Loger(LOG_LVL.ERROR, it)
                    }
                }
            })
    }

    fun sendStatisticPushReceived(subscriberId: String, messageId: String) {
        val jsonObject = JSONObject()
        jsonObject.put("type", "message_read")
        jsonObject.put("token", token)
        val data = JSONObject()
        data.put("message_id", messageId)
        jsonObject.put("data", data)
        sendStatisticEvent(subscriberId, jsonObject)
    }

    fun sendStatisticPushClicked(
        subscriberId: String,
        messageId: String,
        actionId: Int
    ) {
        val jsonObject = JSONObject()
        jsonObject.put("type", "message_clicked")
        jsonObject.put("token", token)
        val data = JSONObject()
        data.put("message_id", messageId)
        data.put("action_id", actionId)
        jsonObject.put("data", data)
        sendStatisticEvent(subscriberId, jsonObject)
    }

    fun sendStatisticPushClosed(subscriberId: String, messageId: String) {
        val jsonObject = JSONObject()
        jsonObject.put("type", "message_closed")
        jsonObject.put("token", token)
        val data = JSONObject()
        data.put("message_id", messageId)
        jsonObject.put("data", data)
        sendStatisticEvent(subscriberId, jsonObject)
    }

    private fun sendStatisticEvent(subscriberId: String, jsonObject: JSONObject) {
        NashpushRestClient.postStatistic(
            "subscribers/$subscriberId/callbacks/",
            jsonObject,
            object : NashpushRestClient.ResponseHandler() {
                override fun onSuccess(response: String?) {
                    if (response != null) {
                        Loger(LOG_LVL.VERBOSE, response)
                    }
                }

                override fun onFailure(statusCode: Int, response: String?, throwable: Throwable?) {
                    super.onFailure(statusCode, response, throwable)
                    throwable?.message?.let {
                        Loger(LOG_LVL.ERROR, it)
                    }
                }
            })
    }

}