package com.nashpush.sdk.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nashpush.sdk.*
import com.nashpush.sdk.notification.entity.ClickActions
import com.nashpush.sdk.notification.entity.MessageData
import com.nashpush.sdk.Nashpush.loger
import com.nashpush.sdk.notification.entity.NotificationBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URL


class FirebaseService : FirebaseMessagingService() {

    override fun onNewToken(s: String) {
        super.onNewToken(s)
        Nashpush.onNewToken(this, s)
    }

    @SuppressLint("LaunchActivityFromNotification")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        loger(Nashpush.LOG_LVL.VERBOSE, "message: $remoteMessage")
        loger(Nashpush.LOG_LVL.VERBOSE, "data from message")
        for ((key, value) in remoteMessage.data) {
            loger(Nashpush.LOG_LVL.VERBOSE, "key: $key   |   value: $value")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = Nashpush.npPrefs?.getString("androidAppName", null) ?: "Notifications"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("121", name, importance)
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
        val messageData = getMessageData(remoteMessage.data)
        var bmp: Bitmap? = null
        try {
            val imageUrl = messageData.notification?.image
            if (imageUrl != null) {
                val input = URL(imageUrl.toString()).openStream()
                bmp = BitmapFactory.decodeStream(input)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val builder = NotificationCompat.Builder(this, "121")
            .setSmallIcon(R.drawable.ic_notifications)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bmp)
            )
            .setContentTitle(messageData.notification?.title)
            .setContentText(messageData.notification?.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        messageData.clickActions.forEach {
            val onClickIntent = Intent(this, OnClickBroadcastReceiver::class.java)
            onClickIntent.putExtra("subscriberId", messageData.subscriberId)
            onClickIntent.putExtra("messageId", messageData.messageId)
            onClickIntent.putExtra("actionId", it.action)
            onClickIntent.putExtra("url", it.clickActionData)
            val onClickPendingIntent =
                PendingIntent.getBroadcast(
                    this.applicationContext,
                    0,
                    onClickIntent,
                    PendingIntent.FLAG_IMMUTABLE.or(Intent.FILL_IN_DATA)
                )
            val btnTittle = it.title
            if (!btnTittle.isNullOrEmpty() && btnTittle != "null") {
                builder.addAction(0, btnTittle, onClickPendingIntent)
            } else {
                builder.setContentIntent(onClickPendingIntent)
            }

        }
        val onCancelIntent = Intent(this, OnDismissBroadcastReceiver::class.java)
        onCancelIntent.putExtra("subscriberId", messageData.subscriberId)
        onCancelIntent.putExtra("messageId", messageData.messageId)
        val onDismissPendingIntent =
            PendingIntent.getBroadcast(
                this.applicationContext,
                0,
                onCancelIntent,
                PendingIntent.FLAG_IMMUTABLE.or(Intent.FILL_IN_DATA)
            )
        builder.setDeleteIntent(onDismissPendingIntent)
        val notification = builder.build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun getMessageData(data: Map<String, String>): MessageData {
        var notificationBody: NotificationBody? = null
        val notificationData = data["notification"]
        if (notificationData != null) {
            val notificationJSON = JSONObject(notificationData)
            notificationBody = NotificationBody(
                notificationJSON.getStringSafe("title"),
                notificationJSON.getStringSafe("body"),
                notificationJSON.getStringSafe("icon"),
                notificationJSON.getStringSafe("image")
            )
        }
        val clickActionString = data["click_actions"]
        val clickActions = ArrayList<ClickActions>()
        if (clickActionString != null) {
            val array = JSONArray(clickActionString)
            for (i in 0 until array.length()) {
                val item = array.getJSONObjectSafe(i)
                if (item != null) {
                    val action = item.getIntSafe("action")
                    val clickActionData = item.getStringSafe("click_action_data")
                    val title = item.getStringSafe("title")
                    clickActions.add(ClickActions(action, clickActionData, title))
                }
            }
        }
        return MessageData(
            clickActions,
            notificationBody,
            data["subscriber_id"],
            data["message_id"]
        )
    }
}