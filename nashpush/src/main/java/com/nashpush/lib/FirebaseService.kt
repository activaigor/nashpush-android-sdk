package com.nashpush.lib

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nashpush.lib.Nashpush.Loger
import org.json.JSONArray
import java.io.IOException
import java.net.URL


class FirebaseService : FirebaseMessagingService() {

    override fun onNewToken(s: String) {
        super.onNewToken(s)
    }

    @SuppressLint("LaunchActivityFromNotification")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val remoteNotification = remoteMessage.notification ?: return
        Loger(Nashpush.LOG_LVL.VERBOSE, "message: $remoteNotification")
        Loger(Nashpush.LOG_LVL.VERBOSE, "data from message")
        for ((key, value) in remoteMessage.data) {
            Loger(Nashpush.LOG_LVL.VERBOSE, "key: $key   |   value: $value")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "test"
            val description = "test notif"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("121", name, importance)
            channel.description = description
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(
                NotificationManager::class.java
            )
            notificationManager.createNotificationChannel(channel)
        }
        var bmp: Bitmap? = null
        try {
            val imageUrl = remoteNotification.imageUrl
            if (imageUrl != null) {
                val input = URL(imageUrl.toString()).openStream()
                bmp = BitmapFactory.decodeStream(input)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val messageData = getMessageData(remoteMessage.data)
        val builder = NotificationCompat.Builder(this, "121")
            .setSmallIcon(R.drawable.ic_notifications)
            .setLargeIcon(bmp)
            .setContentTitle(remoteNotification.title)
            .setContentText(remoteNotification.body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        messageData.clickActions.forEach {
            val onClickIntent = Intent(this, OnClickBroadcastReceiver::class.java)
            onClickIntent.putExtra("subscriberId", messageData.subscriberId)
            onClickIntent.putExtra("messageId", messageData.messageId)
            onClickIntent.putExtra("actionId", it.action)
            onClickIntent.putExtra("url", it.clickActionData)
            val onClickPendingIntent =
                PendingIntent.getBroadcast(this.applicationContext, 0, onClickIntent, PendingIntent.FLAG_IMMUTABLE.or(Intent.FILL_IN_DATA))
            val btnTittle = it.title
            if (!btnTittle.isNullOrEmpty() && btnTittle != "null") {
                builder.addAction(0, btnTittle, onClickPendingIntent)
            } else{
                builder.setContentIntent(onClickPendingIntent)
            }

        }
        val onCancelIntent = Intent(this, OnDismissBroadcastReceiver::class.java)
        onCancelIntent.putExtra("subscriberId", messageData.subscriberId)
        onCancelIntent.putExtra("messageId", messageData.messageId)
        val onDismissPendingIntent =
            PendingIntent.getBroadcast(this.applicationContext, 0, onCancelIntent, PendingIntent.FLAG_IMMUTABLE.or(Intent.FILL_IN_DATA))
        builder.setDeleteIntent(onDismissPendingIntent)
        val notification = builder.build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun getMessageData(data: Map<String, String>): MessageData {
        val clickActionString = data["click_actions"]
        val clickActions = ArrayList<ClickActions>()
        if (clickActionString != null) {
            val array = JSONArray(clickActionString)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                if (item != null) {
                    val action = item.getInt("action")
                    val clickActionData = item.getString("click_action_data")
                    val title = item.getString("title")
                    clickActions.add(ClickActions(action, clickActionData, title))
                }
            }
        }
        return MessageData(clickActions, data["subscriber_id"]?:"", data["message_id"]?:"")
    }
}