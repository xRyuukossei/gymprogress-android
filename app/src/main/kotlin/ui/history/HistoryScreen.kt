package br.com.ryuukossei.gymprogress.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.data.local.SessionSummaryRow
import br.com.ryuukossei.gymprogress.ui.common.EmptyMessage
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.SectionTitle
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatDateTime
import br.com.ryuukossei.gymprogress.ui.common.formatDuration
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(private val repository: GymRepository) : ViewModel() {
    /** Só sessões finalizadas; as em andamento aparecem na aba Treinos. */
    val state: StateFlow<List<SessionSummaryRow>?> =
        repository.observe { repository.dao.observeSessionSummaries() }
            .map { list -> list.filter { it.finishedAt != null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = repository.notifyDataChanged()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenSession: (String) -> Unit) {
    val container = appContainer()
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(container.repository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Histórico") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val sessions = state
        when {
            sessions == null -> LoadingBox(Modifier.padding(padding))
            sessions.isEmpty() -> EmptyMessage("Nenhuma sessão finalizada ainda.", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { SectionTitle("Finalizados (${sessions.size})") }
                items(sessions, key = { it.publicId }) { session ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenSession(session.publicId) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(session.workoutName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${formatDateTime(session.startedAt)} • ${formatDuration(session.startedAt, session.finishedAt)} • ${session.setCount} série${if (session.setCount == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (session.pendingSync) {
                                Text(
                                    "Aguardando envio ao servidor",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = br.com.ryuukossei.gymprogress.ui.theme.WarningColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
