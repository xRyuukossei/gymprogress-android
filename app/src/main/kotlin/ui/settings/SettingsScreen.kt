package br.com.ryuukossei.gymprogress.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.ryuukossei.gymprogress.data.local.LocalCounts
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.data.settings.SettingsState
import br.com.ryuukossei.gymprogress.data.sync.SyncManager
import br.com.ryuukossei.gymprogress.data.sync.SyncScheduler
import br.com.ryuukossei.gymprogress.data.sync.SyncStatus
import br.com.ryuukossei.gymprogress.data.sync.SyncStatusStore
import br.com.ryuukossei.gymprogress.ui.common.SectionTitle
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsActionState(
    val working: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

class SettingsViewModel(
    private val repository: GymRepository,
    settings: StateFlow<SettingsState>,
    private val sync: SyncManager,
    private val syncStatusStore: SyncStatusStore,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    val settings: StateFlow<SettingsState> = settings
    val syncStatus: StateFlow<SyncStatus> = syncStatusStore.state

    val counts: StateFlow<LocalCounts?> =
        repository.observe { repository.dao.observeCounts() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Alterações feitas no celular que ainda não foram enviadas ao servidor. */
    val pending: StateFlow<Int> =
        repository.observe { repository.dao.observePendingChanges() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _action = MutableStateFlow(SettingsActionState())
    val action: StateFlow<SettingsActionState> = _action.asStateFlow()

    fun reimport(discardLocalChanges: Boolean = false) {
        _action.value = SettingsActionState(working = true)
        viewModelScope.launch {
            _action.value = try {
                val r = repository.reimport(discardLocalChanges)
                SettingsActionState(message = "Importados: ${r.workouts} treinos, ${r.exercises} exercícios, ${r.sessions} sessões e ${r.sets} séries.")
            } catch (e: Exception) {
                SettingsActionState(message = e.message ?: "Erro desconhecido.", isError = true)
            }
        }
    }

    fun disconnect() = repository.disconnect()

    fun setAutoSync(enabled: Boolean) {
        syncStatusStore.setAutoSync(enabled)
        if (enabled) {
            scheduler.ensurePeriodic()
            scheduler.syncSoon()
        } else {
            scheduler.cancelPeriodic()
        }
    }

    /** Envia agora (independe do envio automático estar ligado). */
    fun syncNow() {
        _action.value = SettingsActionState(working = true)
        viewModelScope.launch {
            _action.value = try {
                val r = sync.pushPending()
                SettingsActionState(
                    message = if (r.sent == 0) "Nada pendente para enviar." else "Enviado ao servidor: ${r.sent} registro(s)." +
                        if (r.warnings.isNotEmpty()) " Avisos: ${r.warnings.size}." else "",
                    isError = r.warnings.isNotEmpty(),
                )
            } catch (e: Exception) {
                SettingsActionState(message = e.message ?: "Erro ao sincronizar.", isError = true)
            }
        }
    }

    fun dismissMessage() = _action.update { it.copy(message = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(container.repository, container.settings.state, container.sync, container.syncStatus, container.syncScheduler)
            }
        }
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    var confirmReimport by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: PackageManager.NameNotFoundException) {
            "?"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ajustes") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Servidor")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(settings.baseUrl, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Última importação: " + (settings.lastImportAt?.let(::formatDateTime) ?: "nunca"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionTitle("Dados neste celular")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val c = counts
                    if (c == null) {
                        Text("Carregando…")
                    } else {
                        Text("${c.workouts} treinos • ${c.exercises} exercícios")
                        Text("${c.sessions} sessões • ${c.sets} séries")
                    }
                    Text(
                        if (pending == 0) "Nenhuma alteração pendente de envio."
                        else "$pending alteração(ões) feitas neste celular ainda não enviadas ao servidor. Treinos em andamento são enviados quando finalizados.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (pending == 0) MaterialTheme.colorScheme.onSurfaceVariant else br.com.ryuukossei.gymprogress.ui.theme.WarningColor,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            SectionTitle("Envio ao servidor")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enviar automaticamente", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Treinos finalizados vão para o servidor assim que houver internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = syncStatus.autoSyncEnabled, onCheckedChange = vm::setAutoSync)
                    }
                    Text(
                        "Último envio: " + (syncStatus.lastSuccessAt?.let(::formatDateTime) ?: "nunca") +
                            if (syncStatus.running) " (enviando…)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    syncStatus.lastError?.let {
                        Text("Último erro: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    syncStatus.lastWarnings.forEach {
                        Text("Aviso do servidor: $it", style = MaterialTheme.typography.bodySmall, color = br.com.ryuukossei.gymprogress.ui.theme.WarningColor)
                    }
                    OutlinedButton(
                        onClick = vm::syncNow,
                        enabled = !action.working && !syncStatus.running,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sincronizar agora") }
                }
            }

            SectionTitle("Restauração")
            Button(
                onClick = { confirmReimport = true },
                enabled = !action.working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (action.working) "Importando…" else "Reimportar do servidor") }

            action.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (action.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }

            if (pending > 0) {
                OutlinedButton(
                    onClick = { confirmDiscard = true },
                    enabled = !action.working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Descartar alterações do celular e reimportar", color = MaterialTheme.colorScheme.error) }
            }

            OutlinedButton(
                onClick = { confirmDisconnect = true },
                enabled = !action.working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Trocar token / desconectar") }

            Text(
                "GymProgress $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    if (confirmReimport) {
        AlertDialog(
            onDismissRequest = { confirmReimport = false },
            title = { Text("Reimportar do servidor?") },
            text = { Text("Os dados deste celular serão substituídos pelos dados do servidor. Se houver alterações ainda não enviadas, a reimportação é recusada.") },
            confirmButton = {
                TextButton(onClick = { confirmReimport = false; vm.reimport() }) { Text("Reimportar") }
            },
            dismissButton = { TextButton(onClick = { confirmReimport = false }) { Text("Voltar") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Descartar alterações?") },
            text = {
                Text(
                    "As $pending alteração(ões) feitas neste celular e ainda não enviadas ao servidor serão APAGADAS " +
                        "(inclusive treinos registrados aqui), e os dados serão substituídos pelos do servidor. Isso não pode ser desfeito."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; vm.reimport(discardLocalChanges = true) }) {
                    Text("Descartar e reimportar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Voltar") } },
        )
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconectar?") },
            text = { Text("O token será apagado deste celular e o app pedirá um novo. Os dados do celular não são apagados.") },
            confirmButton = {
                TextButton(onClick = { confirmDisconnect = false; vm.disconnect() }) { Text("Desconectar") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Voltar") } },
        )
    }
}
