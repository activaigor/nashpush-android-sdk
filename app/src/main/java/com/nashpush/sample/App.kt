package com.nashpush.sample

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.nashpush.sdk.Nashpush

class App : Application() {

    companion object {
        var settings: SharedPreferences? = null
    }

    override fun onCreate() {
        super.onCreate()
        settings = this.getSharedPreferences("mysettings", Context.MODE_PRIVATE)
        val token = settings?.getString("token", "ODM2NjczOGQyMGU1MDZjZWNlMmE0MDIyZjBiZWE2OTA9Mjg2PS8=")
        Nashpush.setLogLevel(Nashpush.LOG_LVL.VERBOSE)
        Nashpush.initializeApp(this, token?:"")
    }
}