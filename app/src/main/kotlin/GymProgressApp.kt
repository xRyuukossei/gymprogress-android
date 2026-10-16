package br.com.ryuukossei.gymprogress

import android.app.Application

class GymProgressApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
