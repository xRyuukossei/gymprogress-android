package br.com.ryuukossei.gymprogress.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import br.com.ryuukossei.gymprogress.data.local.ExerciseEntity
import br.com.ryuukossei.gymprogress.data.local.TargetMuscleEntity
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.ui.common.EmptyMessage
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.normalizeForSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

data class ExerciseItem(
    val exercise: ExerciseEntity,
    val muscleIds: List<Int>,
    val muscleNames: List<String>,
    /** Em quantos treinos o exercício está hoje. */
    val workoutCount: Int,
)

data class ExercisesUiState(
    val items: List<ExerciseItem>,
    val totalCount: Int,
    val muscles: List<TargetMuscleEntity>,
)

class ExercisesViewModel(private val repository: GymRepository) : ViewModel() {
    private val dao = repository.dao
    private val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply { strength = Collator.PRIMARY }

    val query = MutableStateFlow("")

    private val all = repository.observe {
        combine(
            dao.observeExercises(),
            dao.observeTargetMuscles(),
            dao.observeExerciseMuscleLinks(),
            dao.observeExerciseUsage(),
        ) { exercises, muscles, links, usage ->
            val muscleNames = muscles.associate { it.id to it.name }
            val muscleOrder = muscles.map { it.id }
            val linksByExercise = links.groupBy({ it.exercisePublicId }, { it.targetMuscleId })
            val usageByExercise = usage.associate { it.exercisePublicId to it.workoutCount }
            val items = exercises
                .sortedWith { a, b -> collator.compare(a.name, b.name) }
                .map { e ->
                    val ids = linksByExercise[e.publicId].orEmpty().sortedBy { muscleOrder.indexOf(it) }
                    ExerciseItem(e, ids, ids.mapNotNull { muscleNames[it] }, usageByExercise[e.publicId] ?: 0)
                }
            items to muscles
        }
    }

    val state: StateFlow<ExercisesUiState?> = combine(all, query) { (items, muscles), q ->
        val needle = normalizeForSearch(q)
        val filtered = if (needle.isEmpty()) items else items.filter { item ->
            normalizeForSearch(item.exercise.name).contains(needle) ||
                item.muscleNames.any { normalizeForSearch(it).contains(needle) }
        }
        ExercisesUiState(filtered, items.size, muscles)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = repository.notifyDataChanged()

    /** Salva e devolve null em caso de sucesso, ou a mensagem de erro. */
    fun save(publicId: String?, name: String, comment: String, muscleIds: List<Int>, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                repository.saveExercise(publicId, name, comment, muscleIds)
                null
            } catch (e: IllegalArgumentException) {
                e.message ?: "Dados inválidos."
            }
            onResult(error)
        }
    }

    fun delete(publicId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repository.deleteExercise(publicId)) }
    }
}

/** Exercício aberto no editor: [item] nulo = novo exercício. */
private data class Editing(val item: ExerciseItem?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen() {
    val container = appContainer()
    val vm: ExercisesViewModel = viewModel(
        factory = viewModelFactory { initializer { ExercisesViewModel(container.repository) } }
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirmDelete by remember { mutableStateOf<ExerciseItem?>(null) }
    var blockedDelete by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exercícios") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Editing(null) }) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = "Novo exercício")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val s = state
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar por nome ou músculo") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Limpar busca")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            when {
                s == null -> LoadingBox()
                s.items.isEmpty() -> EmptyMessage(
                    if (s.totalCount == 0) "Nenhum exercício cadastrado. Toque em + para criar o primeiro."
                    else "Nenhum exercício encontrado."
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Espaço no fim para o botão + não cobrir o último item.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            if (query.isBlank()) "${s.totalCount} exercício${if (s.totalCount == 1) "" else "s"}"
                            else "${s.items.size} de ${s.totalCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(s.items, key = { it.exercise.publicId }) { item ->
                        ExerciseCard(
                            item = item,
                            onEdit = { editing = Editing(item) },
                            onDelete = {
                                if (item.workoutCount > 0) {
                                    blockedDelete = "\"${item.exercise.name}\" está em ${item.workoutCount} treino${if (item.workoutCount == 1) "" else "s"}. " +
                                        "Remova-o dos treinos antes de excluir."
                                } else {
                                    confirmDelete = item
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    editing?.let { current ->
        key(current.item?.exercise?.publicId) { ExerciseEditorDialog(
            item = current.item,
            muscles = state?.muscles.orEmpty(),
            onDismiss = { editing = null },
            onSave = { name, comment, muscleIds, onError ->
                vm.save(current.item?.exercise?.publicId, name, comment, muscleIds) { error ->
                    if (error == null) {
                        editing = null
                        scope.launch { snackbar.showSnackbar(if (current.item == null) "Exercício criado" else "Exercício atualizado") }
                    } else {
                        onError(error)
                    }
                }
            },
        ) }
    }

    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir exercício?") },
            text = { Text("\"${item.exercise.name}\" será excluído da biblioteca. Isso não pode ser desfeito.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    vm.delete(item.exercise.publicId) { deleted ->
                        if (deleted) {
                            scope.launch { snackbar.showSnackbar("Exercício excluído") }
                        } else {
                            blockedDelete = "\"${item.exercise.name}\" já foi usado em treinos e tem histórico, por isso não pode ser excluído."
                        }
                    }
                }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Voltar") } },
        )
    }

    blockedDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { blockedDelete = null },
            title = { Text("Não é possível excluir") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { blockedDelete = null }) { Text("Entendi") } },
        )
    }
}

@Composable
private fun ExerciseCard(item: ExerciseItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.muscleNames.isNotEmpty()) {
                    Text(
                        item.muscleNames.joinToString(" • ").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item.exercise.comment?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                if (item.workoutCount > 0) {
                    Text(
                        "Em ${item.workoutCount} treino${if (item.workoutCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.ic_edit), contentDescription = "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Excluir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseEditorDialog(
    item: ExerciseItem?,
    muscles: List<TargetMuscleEntity>,
    onDismiss: () -> Unit,
    onSave: (name: String, comment: String, muscleIds: List<Int>, onError: (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(item?.exercise?.name.orEmpty()) }
    var comment by remember { mutableStateOf(item?.exercise?.comment.orEmpty()) }
    val selected = remember { mutableStateListOf<Int>().apply { addAll(item?.muscleIds.orEmpty()) } }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(if (item == null) "Novo exercício" else "Editar exercício") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100); error = null },
                    label = { Text("Nome *") },
                    placeholder = { Text("Ex.: Supino reto") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(500); error = null },
                    label = { Text("Observação") },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Grupos musculares *", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    muscles.forEach { m ->
                        val isSelected = m.id in selected
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selected.remove(m.id) else selected.add(m.id)
                                error = null
                            },
                            label = { Text(m.name) },
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    saving = true
                    onSave(name, comment, selected.toList()) { message ->
                        error = message
                        saving = false
                    }
                },
            ) { Text(if (saving) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
    )
}
