package com.example.myapplication

import android.app.Application

class SismiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppEventBus.clear()
        AlertUiHandler(this).register()
        SmsAutoHandler(this).register()
        GeofenceHandler(this).register()
    }
}
