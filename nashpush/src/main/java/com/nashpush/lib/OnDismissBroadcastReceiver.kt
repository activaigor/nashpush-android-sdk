package com.nashpush.lib

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class OnDismissBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Nashpush.Loger(Nashpush.LOG_LVL.VERBOSE, "OnDismissBroadcastReceiver: onReceive")
        val subscriberId = intent.getStringExtra("subscriberId")
        val messageId = intent.getStringExtra("messageId")
        if (subscriberId != null && messageId != null) {
            Nashpush.sendStatisticPushClosed(
                subscriberId,
                messageId
            )
        }
    }
}