package com.gnreader.app

import android.app.Application

class GnReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: GnReaderApp
            private set
    }
}
