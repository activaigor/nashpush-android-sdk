package com.nashpush.sdk.notification

import android.app.job.JobService
import android.app.job.JobParameters
import com.nashpush.sdk.Nashpush

class ConnectionService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        Nashpush.getCredentials(this)
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }
}