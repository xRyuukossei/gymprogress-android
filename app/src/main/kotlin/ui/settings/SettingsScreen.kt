package br.com.ryuukossei.gymprogress.ui.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import br.com.ryuukossei.gymprogress.data.settings.SettingsStore
import br.com.ryuukossei.gymprogress.data.sync.SyncManager
import br.com.ryuukossei.gymprogress.data.sync.SyncScheduler
import br.com.ryuukossei.gymprogress.data.sync.SyncStatus
import br.com.ryuukossei.gymprogress.data.sync.SyncStatusStore
import br.com.ryuukossei.gymprogress.ui.common.SectionTitle
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatDateTime
import br.com.ryuukossei.gymprogress.ui.common.userMessage
import br.com.ryuukossei.gymprogress.ui.theme.workoutWarningColor
import kotlinx.coroutines.CancellationException
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
    private val settingsStore: SettingsStore,
    private val sync: SyncManager,
    private val syncStatusStore: SyncStatusStore,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    val settings: StateFlow<SettingsState> = settingsStore.state
    val syncStatus: StateFlow<SyncStatus> = syncStatusStore.state

    val counts: StateFlow<LocalCounts?> =
        repository.observe { repository.dao.observeCounts() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Alterações feitas no celular que ainda não foram enviadas ao servidor. */
    val pending: StateFlow<Int> =
        repository.observe { repository.dao.observePendingChanges() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sendable: StateFlow<Int?> =
        repository.observe { repository.dao.observeSendableChanges() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _action = MutableStateFlow(SettingsActionState())
    val action: StateFlow<SettingsActionState> = _action.asStateFlow()

    fun reimport(discardLocalChanges: Boolean = false) {
        if (_action.value.working) return
        _action.value = SettingsActionState(working = true)
        viewModelScope.launch {
            _action.value = try {
                val r = repository.reimport(discardLocalChanges)
                SettingsActionState(message = "Importados: ${r.workouts} treinos, ${r.exercises} exercícios, ${r.sessions} sessões e ${r.sets} séries.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsActionState(message = e.userMessage(), isError = true)
            }
        }
    }

    fun disconnect() {
        if (_action.value.working) return
        _action.value = SettingsActionState(working = true)
        viewModelScope.launch {
            try { repository.disconnect() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _action.value = SettingsActionState(message = "Não foi possível desconectar. Tente novamente.", isError = true) }
            finally { _action.update { it.copy(working = false) } }
        }
    }

    fun setDarkTheme(enabled: Boolean) = settingsStore.setDarkTheme(enabled)

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
        if (_action.value.working || syncStatus.value.running) return
        _action.value = SettingsActionState(working = true)
        viewModelScope.launch {
            _action.value = try {
                val r = sync.pushPending()
                SettingsActionState(
                    message = when {
                        r.warnings.isNotEmpty() -> "O servidor devolveu ${r.warnings.size} aviso(s). O lote foi mantido pendente para não perder alterações."
                        r.sent == 0 -> "Nenhuma alteração pronta para envio."
                        else -> "Enviado ao servidor: ${r.sent} registro(s)."
                    },
                    isError = r.warnings.isNotEmpty(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SettingsActionState(message = e.userMessage(), isError = true)
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val container = appContainer()
    val vm: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SettingsViewModel(container.repository, container.settings, container.sync, container.syncStatus, container.syncScheduler)
            }
        }
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val sendable by vm.sendable.collectAsStateWithLifecycle()
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
            SectionTitle("Aparência")
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text("Tema escuro", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settings.isDarkTheme) "Ativado. Toque para usar o tema claro."
                            else "Desativado. Toque para usar o tema escuro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.isDarkTheme, onCheckedChange = vm::setDarkTheme)
                }
            }

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

            SectionTitle("Dados e sincronização")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SALVO NESTE CELULAR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text("Seus registros estão guardados", style = MaterialTheme.typography.titleMedium)
                    val c = counts
                    if (c == null) Text("Carregando…") else {
                        Text("${c.workouts} treinos · ${c.exercises} exercícios")
                        Text("${c.sessions} sessões · ${c.sets} séries registradas")
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("ENVIO AO SERVIDOR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    val ready = sendable
                    val title = when {
                        syncStatus.running -> "Enviando…"
                        ready == null || counts == null -> "Carregando situação…"
                        ready > 0 && syncStatus.lastError != null -> "Não foi possível enviar"
                        ready > 0 -> "$ready alterações aguardando envio"
                        syncStatus.lastWarnings.isNotEmpty() -> "Último envio concluído com avisos"
                        pending > 0 -> "Treino em andamento salvo no celular"
                        syncStatus.lastSuccessAt != null -> "✓ Tudo enviado"
                        else -> "Nenhuma alteração pendente"
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium,
                        color = if (ready != null && ready > 0 && syncStatus.lastError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface)
                    if (syncStatus.lastError != null) {
                        Text("Última tentativa: ${syncStatus.lastError}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        Text("Seus dados continuam salvos no celular. Confira a conexão e o acesso ao servidor antes de tentar novamente.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Último envio concluído: " + (syncStatus.lastSuccessAt?.let(::formatDateTime) ?: "nenhum"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (ready != null && pending > ready) {
                        Text("${pending - ready} registros de treinos em andamento aguardam a finalização para serem enviados.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    syncStatus.lastWarnings.forEach {
                        Text("Aviso do servidor: $it", style = MaterialTheme.typography.bodySmall,
                            color = workoutWarningColor())
                    }
                    Button(onClick = vm::syncNow,
                        enabled = !action.working && !syncStatus.running && ready != null && (ready > 0 || syncStatus.lastError != null),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (syncStatus.running) "Enviando…" else if (syncStatus.lastError != null) "Tentar novamente" else "Enviar agora")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Enviar automaticamente", style = MaterialTheme.typography.bodyLarge)
                            Text(if (syncStatus.autoSyncEnabled) "Enviar alterações quando houver conexão."
                                else "Você controla o envio pelo botão acima.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = syncStatus.autoSyncEnabled, onCheckedChange = vm::setAutoSync)
                    }
                    Text("Treinos em andamento só são enviados depois de finalizados.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionTitle("Restauração")
            Button(
                onClick = { confirmReimport = true },
                enabled = !action.working,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (action.working) "Aguarde…" else "Reimportar do servidor") }

            action.message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (action.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
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
