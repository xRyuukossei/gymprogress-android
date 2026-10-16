package br.com.ryuukossei.gymprogress

import android.content.Context
import br.com.ryuukossei.gymprogress.data.draft.SessionDraftStore
import br.com.ryuukossei.gymprogress.data.local.AppDatabase
import br.com.ryuukossei.gymprogress.data.remote.GymApi
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.data.settings.SettingsStore
import br.com.ryuukossei.gymprogress.data.sync.SyncManager
import br.com.ryuukossei.gymprogress.data.sync.SyncScheduler
import br.com.ryuukossei.gymprogress.data.sync.SyncStatusStore
import br.com.ryuukossei.gymprogress.timer.RestTimer

/** Monta as dependências do app uma única vez (injeção de dependências manual). */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val database = AppDatabase.build(context)
    private val api = GymApi()
    val syncStatus = SyncStatusStore(context)
    val syncScheduler = SyncScheduler(context)
    val repository = GymRepository(database, api, settings) {
        if (syncStatus.state.value.autoSyncEnabled) syncScheduler.syncSoon()
    }
    val sync = SyncManager(database, api, settings, syncStatus)
    val drafts = SessionDraftStore(context)
    val restTimer = RestTimer(context)
}
