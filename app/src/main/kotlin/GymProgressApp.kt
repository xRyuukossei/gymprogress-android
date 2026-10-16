package br.com.ryuukossei.gymprogress

import android.app.Application

class GymProgressApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Envia pendências ao abrir o app (e mantém um envio periódico de segurança).
        if (container.syncStatus.state.value.autoSyncEnabled) {
            container.syncScheduler.syncSoon()
            container.syncScheduler.ensurePeriodic()
        }
    }
}
