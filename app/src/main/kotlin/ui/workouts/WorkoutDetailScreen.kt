package br.com.ryuukossei.gymprogress.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.ryuukossei.gymprogress.R
import br.com.ryuukossei.gymprogress.data.local.GymDao
import br.com.ryuukossei.gymprogress.data.local.PlannedExerciseRow
import br.com.ryuukossei.gymprogress.data.local.SessionEntity
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.ui.common.formatDateTime
import br.com.ryuukossei.gymprogress.data.local.WorkoutEntity
import br.com.ryuukossei.gymprogress.ui.common.EmptyMessage
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.SectionTitle
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatRest
import br.com.ryuukossei.gymprogress.ui.common.formatWeight
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlannedExerciseItem(val row: PlannedExerciseRow, val muscles: List<String>)

data class WorkoutDetailUiState(
    val workout: WorkoutEntity?,
    val exercises: List<PlannedExerciseItem>,
    /** Sessão deste treino ainda em andamento, se houver. */
    val inProgress: SessionEntity?,
)

class WorkoutDetailViewModel(private val repository: GymRepository, private val workoutId: String) : ViewModel() {
    private val dao: GymDao = repository.dao

    val state: StateFlow<WorkoutDetailUiState?> = repository.observe {
        combine(
            dao.observeWorkout(workoutId),
            dao.observePlannedExercises(workoutId),
            dao.observeExerciseMuscles(),
            dao.observeInProgressSessionOfWorkout(workoutId),
        ) { workout, planned, muscles, inProgress ->
            val musclesByExercise = muscles.groupBy({ it.exercisePublicId }, { it.muscleName })
            WorkoutDetailUiState(
                workout = workout,
                exercises = planned.map { PlannedExerciseItem(it, musclesByExercise[it.exercisePublicId].orEmpty()) },
                inProgress = inProgress,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun start(onStarted: (String) -> Unit) {
        viewModelScope.launch { onStarted(repository.startSession(workoutId)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(workoutId: String, onBack: () -> Unit, onRunSession: (String) -> Unit) {
    val container = appContainer()
    val vm: WorkoutDetailViewModel = viewModel(
        key = "workout-$workoutId",
        factory = viewModelFactory { initializer { WorkoutDetailViewModel(container.repository, workoutId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmContinue by remember { mutableStateOf<SessionEntity?>(null) }
    var starting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.workout?.name ?: "Treino", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Voltar")
                    }
                },
            )
        },
        bottomBar = {
            val s = state
            if (s?.workout != null && s.exercises.isNotEmpty()) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    val inProgress = s.inProgress
                    Button(
                        onClick = {
                            if (inProgress != null) {
                                confirmContinue = inProgress
                            } else if (!starting) {
                                starting = true
                                vm.start { id -> starting = false; onRunSession(id) }
                            }
                        },
                        enabled = !starting,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(if (inProgress != null) "Continuar treino em andamento" else "Iniciar treino")
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val s = state
        val workout = s?.workout
        when {
            s == null -> LoadingBox(Modifier.padding(padding))
            workout == null -> EmptyMessage("Treino não encontrado.", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(workout.category.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        workout.comment?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { SectionTitle("Exercícios planejados (${s.exercises.size})") }
                if (s.exercises.isEmpty()) {
                    item { Text("Nenhum exercício planejado.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(s.exercises, key = { it.row.publicId }) { PlannedExerciseCard(it) }
                item {
                    Text(
                        "Editar o planejamento pelo app chega numa próxima etapa.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
    confirmContinue?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmContinue = null },
            title = { Text("Continuar treino?") },
            text = { Text("Reabrir o treino iniciado em ${formatDateTime(session.startedAt)}.") },
            confirmButton = {
                TextButton(onClick = { confirmContinue = null; onRunSession(session.publicId) }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { confirmContinue = null }) { Text("Voltar") } },
        )
    }
}

@Composable
private fun PlannedExerciseCard(item: PlannedExerciseItem) {
    val row = item.row
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.exerciseName, style = MaterialTheme.typography.titleMedium)
            if (item.muscles.isNotEmpty()) {
                Text(
                    item.muscles.joinToString(" • ").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "${row.sets} × ${row.repetitions}  •  ${formatWeight(row.weight)}  •  descanso ${formatRest(row.restTimeSeconds)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            row.notes?.takeIf { it.isNotBlank() }?.let {
                Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
