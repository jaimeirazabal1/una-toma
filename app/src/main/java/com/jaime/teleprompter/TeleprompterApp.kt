package com.jaime.teleprompter

import android.app.Application

class TeleprompterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
