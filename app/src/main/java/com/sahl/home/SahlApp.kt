package com.sahl.home

import android.app.Application

class SahlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Sahl.catchCrashes(this)
    }
}
