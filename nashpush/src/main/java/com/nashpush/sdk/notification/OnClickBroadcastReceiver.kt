package com.nashpush.sdk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.nashpush.sdk.Nashpush

class OnClickBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Nashpush.loger(Nashpush.LOG_LVL.VERBOSE, "OnClickBroadcastReceiver: onReceive")
        val notificationIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse(intent.getStringExtra("url")))
        notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val subscriberId = intent.getStringExtra("subscriberId")
        val messageId = intent.getStringExtra("messageId")
        val actionId = intent.getIntExtra("actionId", 0)
        if (subscriberId != null && messageId != null) {
            Nashpush.sendStatisticPushClicked(
                subscriberId,
                messageId,
                actionId
            )
        }
        try {
            context.startActivity(notificationIntent)
        } catch (e: RuntimeException) {
            Toast.makeText(context, "No app found to handle click", Toast.LENGTH_SHORT).show()
        }
    }
}