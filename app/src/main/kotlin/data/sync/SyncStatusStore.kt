package br.com.ryuukossei.gymprogress.data.sync

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncStatus(
    /** Envio automático ligado. Começa desligado: o usuário liga depois de conferir as pendências. */
    val autoSyncEnabled: Boolean,
    val lastSuccessAt: Long?,
    val lastError: String?,
    val lastWarnings: List<String>,
    val running: Boolean = false,
)

/** Estado da sincronização mostrado em Ajustes (persistido entre aberturas do app). */
class SyncStatusStore(context: Context) {
    private val prefs = context.getSharedPreferences("gymprogress_sync", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SyncStatus> = _state.asStateFlow()

    fun setAutoSync(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO, enabled) }
        _state.value = _state.value.copy(autoSyncEnabled = enabled)
    }

    fun setRunning(running: Boolean) {
        _state.value = _state.value.copy(running = running)
    }

    fun recordSuccess(warnings: List<String>) {
        val now = System.currentTimeMillis()
        prefs.edit {
            putLong(KEY_LAST_SUCCESS, now)
            remove(KEY_LAST_ERROR)
            putString(KEY_WARNINGS, warnings.joinToString(SEPARATOR))
        }
        _state.value = _state.value.copy(lastSuccessAt = now, lastError = null, lastWarnings = warnings)
    }

    fun recordError(message: String) {
        prefs.edit { putString(KEY_LAST_ERROR, message) }
        _state.value = _state.value.copy(lastError = message)
    }

    private fun load() = SyncStatus(
        autoSyncEnabled = prefs.getBoolean(KEY_AUTO, false),
        lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L },
        lastError = prefs.getString(KEY_LAST_ERROR, null),
        lastWarnings = prefs.getString(KEY_WARNINGS, null)?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty(),
    )

    companion object {
        private const val KEY_AUTO = "auto_sync"
        private const val KEY_LAST_SUCCESS = "last_success_at"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_WARNINGS = "last_warnings"
        private const val SEPARATOR = "\n"
    }
}
