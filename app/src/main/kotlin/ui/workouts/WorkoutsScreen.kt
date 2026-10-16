package br.com.ryuukossei.gymprogress.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.ryuukossei.gymprogress.data.local.GymDao
import br.com.ryuukossei.gymprogress.data.local.SessionSummaryRow
import br.com.ryuukossei.gymprogress.data.local.WorkoutEntity
import br.com.ryuukossei.gymprogress.ui.common.EmptyMessage
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.SectionTitle
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatDateTime
import br.com.ryuukossei.gymprogress.ui.common.isCompletedSession
import br.com.ryuukossei.gymprogress.ui.theme.SuccessColor
import br.com.ryuukossei.gymprogress.ui.theme.WarningColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.Collator
import java.util.Locale

data class WorkoutItem(val workout: WorkoutEntity, val completedCount: Int)

data class WorkoutsUiState(
    val inProgress: List<SessionSummaryRow>,
    val active: List<WorkoutItem>,
    val archived: List<WorkoutItem>,
)

class WorkoutsViewModel(dao: GymDao) : ViewModel() {
    private val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }

    val state: StateFlow<WorkoutsUiState?> =
        combine(dao.observeWorkouts(), dao.observeSessionSummaries()) { workouts, sessions ->
            val completedByWorkout = sessions
                .filter { isCompletedSession(it.startedAt, it.finishedAt) }
                .groupingBy { it.workoutPublicId }
                .eachCount()
            val items = workouts
                .sortedWith { a, b -> collator.compare(a.name, b.name) }
                .map { WorkoutItem(it, completedByWorkout[it.publicId] ?: 0) }
            WorkoutsUiState(
                inProgress = sessions.filter { it.finishedAt == null },
                active = items.filter { !it.workout.isArchived },
                archived = items.filter { it.workout.isArchived },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    onOpenWorkout: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val container = appContainer()
    val vm: WorkoutsViewModel = viewModel(factory = viewModelFactory { initializer { WorkoutsViewModel(container.repository.dao) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Treinos") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val s = state
        when {
            s == null -> LoadingBox(Modifier.padding(padding))
            s.active.isEmpty() && s.archived.isEmpty() && s.inProgress.isEmpty() ->
                EmptyMessage("Nenhum treino encontrado.", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (s.inProgress.isNotEmpty()) {
                    item { SectionTitle("Em andamento", color = WarningColor) }
                    items(s.inProgress, key = { "s-" + it.publicId }) { session ->
                        InProgressCard(session) { onOpenSession(session.publicId) }
                    }
                    item {
                        Text(
                            "Continuar ou cancelar um treino pelo app chega na próxima etapa.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                item { SectionTitle("Meus treinos (${s.active.size})") }
                items(s.active, key = { it.workout.publicId }) { item ->
                    WorkoutCard(item) { onOpenWorkout(item.workout.publicId) }
                }

                if (s.archived.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showArchived = !showArchived }) {
                                Text(if (showArchived) "Ocultar arquivados" else "Mostrar arquivados (${s.archived.size})")
                            }
                        }
                    }
                    if (showArchived) {
                        items(s.archived, key = { it.workout.publicId }) { item ->
                            WorkoutCard(item) { onOpenWorkout(item.workout.publicId) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InProgressCard(session: SessionSummaryRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = WarningColor.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(session.workoutName, style = MaterialTheme.typography.titleMedium)
            Text(
                "Iniciado ${formatDateTime(session.startedAt)} • ${session.setCount} série${if (session.setCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkoutCard(item: WorkoutItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.workout.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (item.workout.isArchived) {
                    Text("arquivado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                item.workout.category.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            item.workout.comment?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${item.completedCount} concluído${if (item.completedCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium,
                color = SuccessColor,
            )
        }
    }
}
