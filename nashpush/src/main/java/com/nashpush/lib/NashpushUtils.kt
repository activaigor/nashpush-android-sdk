package com.nashpush.lib

import android.os.Handler
import android.os.Looper

val isRunningOnMainThread: Boolean
    get() = Thread.currentThread() == Looper.getMainLooper().thread

fun runOnMainUIThread(runnable: Runnable) {
    if (Looper.getMainLooper().thread === Thread.currentThread()) runnable.run() else {
        val handler = Handler(Looper.getMainLooper())
        handler.post(runnable)
    }
}

fun runOnMainThreadDelayed(runnable: Runnable?, delay: Int) {
    val handler = Handler(Looper.getMainLooper())
    handler.postDelayed(runnable!!, delay.toLong())
}