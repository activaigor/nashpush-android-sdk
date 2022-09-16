package com.nashpush.sdk

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Context.JOB_SCHEDULER_SERVICE
import android.content.SharedPreferences
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.nashpush.sdk.notification.ConnectionService
import com.nashpush.sdk.rest.NashpushRestClient
import org.json.JSONObject


object Nashpush {

    enum class LOG_LVL {
        NONE, FATAL, ERROR, WARN, INFO, DEBUG, VERBOSE
    }

    fun loger(logLvl: LOG_LVL, message: String) {
        loger(logLvl, message, null)
    }

    fun loger(logLvl: LOG_LVL, message: String, throwable: Throwable?) {
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
    private var firebaseToken = ""
    private var firebaseApp: FirebaseApp? = null
    var npPrefs: SharedPreferences? = null

    fun setLogLevel(logLevel: LOG_LVL) {
        logCatLevel = logLevel
    }

    fun initializeApp(context: Context, channelToken: String) {
        npPrefs = getPrefs(context)
        saveString(context, "channelToken", channelToken)
        NashpushRestClient.setChannelToken(channelToken)
        checkFirebaseToken(context)
        startJob(context)
    }

    private fun saveString(context: Context, key: String, value: String?) {
        val editor: SharedPreferences.Editor? = getPrefs(context)?.edit()
        editor?.putString(key, value)
        editor?.apply()
    }

    private fun startJob(context: Context, minimum: Long = 1, maximum: Long = 1) {
        val componentName = ComponentName(context, ConnectionService::class.java)
        val info = JobInfo.Builder(1, componentName)
            .setMinimumLatency(minimum)
            .setOverrideDeadline(maximum)
            .build()

        val scheduler = context.getSystemService(JOB_SCHEDULER_SERVICE) as JobScheduler?
        val resultCode = scheduler?.schedule(info)
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            loger(LOG_LVL.VERBOSE, "Job Scheduled")
        } else {
            loger(LOG_LVL.ERROR, "Job Scheduling fail")
        }
    }

    fun getCredentials(context: Context) {
        val channelToken = getPrefs(context)?.getString("channelToken", null)
        if (channelToken.isNullOrEmpty()) {
            loger(LOG_LVL.ERROR, "Init error: channelToken not set!")
            return
        }
        NashpushRestClient.get(
            "firebase-credentials/",
            object : NashpushRestClient.ResponseHandler() {
                override fun onSuccess(response: String?) {
                    if (response != null) {
                        val body = JSONObject(response)
                        val androidAppName = body.getStringSafe("android_app_name")
                        val androidApp = body.getJSONObjectSafe("android_app")
                        val configUuid = androidApp?.getStringSafe("config_uuid")

                        val firebaseConfig = getFirebaseConfig(body)
                        val oldFirebaseConfig = getPrefs(context)?.getString("firebaseConfig", null)

                        if (!oldFirebaseConfig.isNullOrEmpty()) {
                            val config = getFirebaseConfig(JSONObject(oldFirebaseConfig))
                            if (firebaseConfig == config) {
                                loger(
                                    LOG_LVL.VERBOSE,
                                    "Data actual, create firebase instance",
                                )
                                return
                            }
                        }
                        saveString(context, "androidAppName", androidAppName)
                        saveString(context, "firebaseConfig", response)
                        saveString(context, "configUuid", configUuid)

                        firebaseApp?.delete()

                        if (firebaseConfig != null && configUuid != null) {
                            handleDataAndSubscribe(
                                context,
                                firebaseConfig,
                                configUuid
                            )
                        } else {
                            restartJob(context)
                        }
                    }
                }

                override fun onFailure(statusCode: Int, response: String?, throwable: Throwable?) {
                    super.onFailure(statusCode, response, throwable)
                    restartJob(context)
                    throwable?.message?.let {
                        loger(LOG_LVL.ERROR, it)
                    }
                }
            })
    }

    private fun getFirebaseConfig(body: JSONObject): FirebaseConfig? {
        val senderId = body.getStringSafe("sender_id")
        val androidApp = body.getJSONObjectSafe("android_app")
        val projectId = androidApp?.getStringSafe("project_id")
        val appId = androidApp?.getStringSafe("app_id")
        val apiKey = androidApp?.getStringSafe("api_key")
        return if (senderId != null
            && projectId != null
            && appId != null
            && apiKey != null
        ) {
            FirebaseConfig(
                senderId,
                projectId,
                appId,
                apiKey
            )
        } else {
            null
        }
    }

    private fun restartJob(context: Context) {
        startJob(context, 20 * 60 * 1000, 30 * 60 * 1000)
    }

    private fun handleDataAndSubscribe(
        context: Context,
        firebaseConfig: FirebaseConfig,
        configUuid: String
    ) {
        var firebaseMessaging: FirebaseMessaging? = null

        val firebaseOptions = FirebaseOptions.Builder()
            .setGcmSenderId(firebaseConfig.senderId)
            .setApplicationId(firebaseConfig.appId)
            .setApiKey(firebaseConfig.apiKey)
            .setProjectId(firebaseConfig.projectId)
            .build()

        firebaseApp = FirebaseApp.initializeApp(
            context,
            firebaseOptions
        )

        firebaseMessaging = firebaseApp?.get(FirebaseMessaging::class.java) as FirebaseMessaging

        firebaseMessaging.token
            .addOnCompleteListener(object : OnCompleteListener<String?> {
                override fun onComplete(task: Task<String?>) {
                    if (!task.isSuccessful) {
                        restartJob(context)
                        loger(
                            LOG_LVL.VERBOSE,
                            "Fetching FCM registration token failed",
                            task.exception
                        )
                        return
                    }

                    // Get new FCM registration token
                    firebaseToken = task.result ?: ""
                    saveString(context, "firebaseToken", firebaseToken)
                    loger(LOG_LVL.VERBOSE, "FirebaseToken: $firebaseToken")
                    sendTokenToBackend(context, firebaseToken, configUuid)
                }
            })

    }

    private fun sendTokenToBackend(context: Context, token: String, configUuid: String) {
        val jsonObject = JSONObject()
        jsonObject.put("token", token)
        jsonObject.put("firebase_app", configUuid)
        jsonObject.put("is_android", true)
        NashpushRestClient.post(
            "subscribers/",
            jsonObject,
            object : NashpushRestClient.ResponseHandler() {
                override fun onSuccess(response: String?) {
                    if (response != null) {
                        val body = JSONObject(response)
                        val uuid = body.getStringSafe("uuid")
                        loger(LOG_LVL.VERBOSE, "uuid: $uuid")
                    }
                }

                override fun onFailure(statusCode: Int, response: String?, throwable: Throwable?) {
                    super.onFailure(statusCode, response, throwable)
                    restartJob(context)
                    throwable?.message?.let {
                        loger(LOG_LVL.ERROR, it)
                    }
                }
            })
    }

    fun sendStatisticPushReceived(subscriberId: String, messageId: String) {
        val jsonObject = JSONObject()
        jsonObject.put("type", "message_read")
        jsonObject.put("token", firebaseToken)
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
        jsonObject.put("token", firebaseToken)
        val data = JSONObject()
        data.put("message_id", messageId)
        data.put("action_id", actionId)
        jsonObject.put("data", data)
        sendStatisticEvent(subscriberId, jsonObject)
    }

    fun sendStatisticPushClosed(subscriberId: String, messageId: String) {
        val jsonObject = JSONObject()
        jsonObject.put("type", "message_closed")
        jsonObject.put("token", firebaseToken)
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
                        loger(LOG_LVL.VERBOSE, response)
                    }
                }

                override fun onFailure(statusCode: Int, response: String?, throwable: Throwable?) {
                    super.onFailure(statusCode, response, throwable)
                    throwable?.message?.let {
                        loger(LOG_LVL.ERROR, it)
                    }
                }
            })
    }

    fun checkFirebaseToken(context: Context) {
        val token = getString(context, "firebaseToken")
        val firebaseConfig = getPrefs(context)?.getString("firebaseConfig", null)
        val configUuid = getPrefs(context)?.getString("configUuid", null)
        if (!token.isNullOrEmpty()
            && !configUuid.isNullOrEmpty()
            && !firebaseConfig.isNullOrEmpty()
        ) {
            val config = getFirebaseConfig(JSONObject(firebaseConfig))
            config?.let { handleDataAndSubscribe(context, it, configUuid) }
        }
    }

    private fun getString(context: Context, key: String): String? {
        return getPrefs(context)?.getString(key, null)
    }

    private fun getPrefs(context: Context): SharedPreferences? {
        if (npPrefs == null) {
            npPrefs = context.getSharedPreferences("np_prefs", Context.MODE_PRIVATE)
        }
        return npPrefs
    }

    fun onNewToken(context: Context, token: String) {
        val configUuid = getPrefs(context)?.getString("configUuid", null)
        val firebaseToken = getPrefs(context)?.getString("firebaseToken", null)
        if (token != firebaseToken) {
            saveString(context, "firebaseToken", token)
            loger(LOG_LVL.VERBOSE, "FirebaseToken changed: $token")
            configUuid?.let {
                sendTokenToBackend(context, token, it)
            }
        }
    }

}