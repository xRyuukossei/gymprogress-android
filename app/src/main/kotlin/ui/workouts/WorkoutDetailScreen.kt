package br.com.ryuukossei.gymprogress.ui.workouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.ryuukossei.gymprogress.R
import br.com.ryuukossei.gymprogress.data.local.GymDao
import br.com.ryuukossei.gymprogress.data.local.PlannedExerciseRow
import br.com.ryuukossei.gymprogress.data.local.SessionEntity
import br.com.ryuukossei.gymprogress.data.local.TargetMuscleEntity
import br.com.ryuukossei.gymprogress.data.local.WorkoutEntity
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.data.repository.GymRepository.PlannedValues
import br.com.ryuukossei.gymprogress.ui.common.EmptyMessage
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.SectionTitle
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatDateTime
import br.com.ryuukossei.gymprogress.ui.common.formatRest
import br.com.ryuukossei.gymprogress.ui.common.formatWeight
import br.com.ryuukossei.gymprogress.ui.exercises.ExerciseEditorDialog
import br.com.ryuukossei.gymprogress.ui.exercises.ExerciseItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

data class PlannedExerciseItem(val row: PlannedExerciseRow, val muscles: List<String>)

data class WorkoutDetailUiState(
    val workout: WorkoutEntity?,
    val exercises: List<PlannedExerciseItem>,
    /** Sessão deste treino ainda em andamento, se houver. */
    val inProgress: SessionEntity?,
    /** Biblioteca de exercícios, para adicionar ao treino. */
    val library: List<ExerciseItem>,
    val muscles: List<TargetMuscleEntity>,
    /** Categorias já usadas nos treinos (atalho ao editar). */
    val categories: List<String>,
)

class WorkoutDetailViewModel(private val repository: GymRepository, private val workoutId: String) : ViewModel() {
    private val dao: GymDao = repository.dao
    private val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }

    private val workoutPart = repository.observe {
        combine(
            dao.observeWorkout(workoutId),
            dao.observePlannedExercises(workoutId),
            dao.observeExerciseMuscles(),
            dao.observeInProgressSessionOfWorkout(workoutId),
        ) { workout, planned, muscles, inProgress ->
            val musclesByExercise = muscles.groupBy({ it.exercisePublicId }, { it.muscleName })
            Triple(
                workout,
                planned.map { PlannedExerciseItem(it, musclesByExercise[it.exercisePublicId].orEmpty()) },
                inProgress,
            )
        }
    }

    private val libraryPart = repository.observe {
        combine(
            dao.observeExercises(),
            dao.observeTargetMuscles(),
            dao.observeExerciseMuscleLinks(),
            dao.observeWorkouts(),
        ) { exercises, muscles, links, workouts ->
            val names = muscles.associate { it.id to it.name }
            val order = muscles.map { it.id }
            val linksByExercise = links.groupBy({ it.exercisePublicId }, { it.targetMuscleId })
            val library = exercises
                .sortedWith { a, b -> collator.compare(a.name, b.name) }
                .map { e ->
                    val ids = linksByExercise[e.publicId].orEmpty().sortedBy { order.indexOf(it) }
                    ExerciseItem(e, ids, ids.mapNotNull { names[it] }, workoutCount = 0)
                }
            val categories = workouts.map { it.category.trim() }
                .filter { it.isNotEmpty() }
                .distinctBy { it.lowercase() }
                .sortedWith { a, b -> collator.compare(a, b) }
            Triple(library, muscles, categories)
        }
    }

    val state: StateFlow<WorkoutDetailUiState?> = combine(workoutPart, libraryPart) { w, l ->
        WorkoutDetailUiState(
            workout = w.first,
            exercises = w.second,
            inProgress = w.third,
            library = l.first,
            muscles = l.second,
            categories = l.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = repository.notifyDataChanged()

    fun start(onStarted: (String) -> Unit) {
        viewModelScope.launch { onStarted(repository.startSession(workoutId)) }
    }

    /** Executa uma gravação; devolve null em caso de sucesso ou a mensagem de erro de validação. */
    private fun run(onResult: (String?) -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            val error = try {
                block(); null
            } catch (e: IllegalArgumentException) {
                e.message ?: "Dados inválidos."
            }
            onResult(error)
        }
    }

    fun saveWorkout(name: String, category: String, comment: String, onResult: (String?) -> Unit) =
        run(onResult) { repository.saveWorkout(workoutId, name, category, comment) }

    fun setArchived(archived: Boolean, onDone: () -> Unit) {
        viewModelScope.launch { repository.setWorkoutArchived(workoutId, archived); onDone() }
    }

    fun addExercise(exerciseId: String, values: PlannedValues, onResult: (String?) -> Unit) =
        run(onResult) { repository.addPlannedExercise(workoutId, exerciseId, values) }

    fun updateExercise(publicId: String, values: PlannedValues, onResult: (String?) -> Unit) =
        run(onResult) { repository.updatePlannedExercise(publicId, values) }

    fun removeExercise(publicId: String) {
        viewModelScope.launch { repository.removePlannedExercise(publicId) }
    }

    fun move(publicId: String, direction: Int) {
        viewModelScope.launch { repository.movePlannedExercise(workoutId, publicId, direction) }
    }

    fun createExercise(name: String, comment: String, muscleIds: List<Int>, onResult: (id: String?, error: String?) -> Unit) {
        viewModelScope.launch {
            try {
                onResult(repository.saveExercise(null, name, comment, muscleIds), null)
            } catch (e: IllegalArgumentException) {
                onResult(null, e.message ?: "Dados inválidos.")
            }
        }
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
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    var confirmContinue by remember { mutableStateOf<SessionEntity?>(null) }
    var starting by remember { mutableStateOf(false) }
    var editingWorkout by remember { mutableStateOf(false) }
    var confirmArchive by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var creatingExercise by remember { mutableStateOf(false) }
    var newExerciseId by remember { mutableStateOf<String?>(null) }
    var editingPlanned by remember { mutableStateOf<PlannedExerciseItem?>(null) }
    var confirmRemove by remember { mutableStateOf<PlannedExerciseItem?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun toast(message: String) { scope.launch { snackbar.showSnackbar(message) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.workout?.name ?: "Treino", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Voltar")
                    }
                },
                actions = {
                    val workout = state?.workout
                    if (workout != null) {
                        IconButton(onClick = { editingWorkout = true }) {
                            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Editar treino")
                        }
                        IconButton(onClick = { confirmArchive = true }) {
                            Icon(
                                painterResource(if (workout.isArchived) R.drawable.ic_unarchive else R.drawable.ic_archive),
                                contentDescription = if (workout.isArchived) "Restaurar treino" else "Arquivar treino",
                            )
                        }
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
        snackbarHost = { SnackbarHost(snackbar) },
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(workout.category.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            if (workout.isArchived) {
                                Text("ARQUIVADO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        workout.comment?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Exercícios planejados (${s.exercises.size})", Modifier.weight(1f))
                        OutlinedButton(onClick = { newExerciseId = null; adding = true }) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                            Text(" Adicionar")
                        }
                    }
                }
                if (s.exercises.isEmpty()) {
                    item { Text("Nenhum exercício planejado. Toque em Adicionar.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                itemsIndexed(s.exercises, key = { _, it -> it.row.publicId }) { index, item ->
                    PlannedExerciseCard(
                        item = item,
                        canMoveUp = index > 0,
                        canMoveDown = index < s.exercises.lastIndex,
                        onMoveUp = { vm.move(item.row.publicId, -1) },
                        onMoveDown = { vm.move(item.row.publicId, +1) },
                        onEdit = { editingPlanned = item },
                        onRemove = { confirmRemove = item },
                    )
                }
            }
        }
    }

    val s = state
    val workout = s?.workout

    if (editingWorkout && workout != null) {
        WorkoutEditorDialog(
            title = "Editar treino",
            initialName = workout.name,
            initialCategory = workout.category,
            initialComment = workout.comment.orEmpty(),
            categories = s.categories,
            onDismiss = { editingWorkout = false },
            onSave = { name, category, comment, onError ->
                vm.saveWorkout(name, category, comment) { error ->
                    if (error == null) { editingWorkout = false; toast("Treino atualizado") } else onError(error)
                }
            },
        )
    }

    if (confirmArchive && workout != null) {
        val archiving = !workout.isArchived
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(if (archiving) "Arquivar treino?" else "Restaurar treino?") },
            text = {
                Text(
                    if (archiving) "\"${workout.name}\" sai da lista principal, mas o histórico é mantido. Você pode restaurá-lo depois em \"Mostrar arquivados\"."
                    else "\"${workout.name}\" volta para a lista principal de treinos."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmArchive = false
                    vm.setArchived(archiving) { if (archiving) onBack() else toast("Treino restaurado") }
                }) { Text(if (archiving) "Arquivar" else "Restaurar") }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Voltar") } },
        )
    }

    if (adding && s != null) {
        AddPlannedExerciseDialog(
            exercises = s.library,
            alreadyInWorkout = s.exercises.map { it.row.exercisePublicId }.toSet(),
            onCreateExercise = { creatingExercise = true },
            preselectedId = newExerciseId,
            onDismiss = { adding = false },
            onSave = { exerciseId, values, onError ->
                vm.addExercise(exerciseId, values) { error ->
                    if (error == null) { adding = false; toast("Exercício adicionado") } else onError(error)
                }
            },
        )
    }

    if (creatingExercise && s != null) {
        ExerciseEditorDialog(
            item = null,
            muscles = s.muscles,
            onDismiss = { creatingExercise = false },
            onSave = { name, comment, muscleIds, onError ->
                vm.createExercise(name, comment, muscleIds) { id, error ->
                    if (id != null) { creatingExercise = false; newExerciseId = id } else onError(error ?: "Erro ao salvar.")
                }
            },
        )
    }

    editingPlanned?.let { item ->
        EditPlannedDialog(
            exerciseName = item.row.exerciseName,
            initial = PlannedValues(item.row.sets, item.row.repetitions, item.row.weight, item.row.restTimeSeconds, item.row.notes),
            onDismiss = { editingPlanned = null },
            onSave = { values, onError ->
                vm.updateExercise(item.row.publicId, values) { error ->
                    if (error == null) { editingPlanned = null; toast("Exercício atualizado") } else onError(error)
                }
            },
        )
    }

    confirmRemove?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remover do treino?") },
            text = { Text("\"${item.row.exerciseName}\" sai deste treino. O exercício continua na biblioteca e as séries já feitas continuam no histórico.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    vm.removeExercise(item.row.publicId)
                    toast("Exercício removido do treino")
                }) { Text("Remover", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Voltar") } },
        )
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
private fun PlannedExerciseCard(
    item: PlannedExerciseItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val row = item.row
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
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
                Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = "Mover para cima", tint = if (canMoveUp) iconTint else iconTint.copy(alpha = 0.3f))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = "Mover para baixo", tint = if (canMoveDown) iconTint else iconTint.copy(alpha = 0.3f))
                }
                IconButton(onClick = onEdit) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "Editar", tint = iconTint)
                }
                IconButton(onClick = onRemove) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = "Remover do treino", tint = iconTint)
                }
            }
        }
    }
}
