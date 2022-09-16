package com.nashpush.sdk.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nashpush.sdk.Nashpush

class OnDismissBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Nashpush.loger(Nashpush.LOG_LVL.VERBOSE, "OnDismissBroadcastReceiver: onReceive")
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