package br.com.ryuukossei.gymprogress

import android.content.Context
import br.com.ryuukossei.gymprogress.data.local.AppDatabase
import br.com.ryuukossei.gymprogress.data.remote.GymApi
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.data.settings.SettingsStore

/** Monta as dependências do app uma única vez (injeção de dependências manual). */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val database = AppDatabase.build(context)
    private val api = GymApi()
    val repository = GymRepository(database, api, settings)
}
