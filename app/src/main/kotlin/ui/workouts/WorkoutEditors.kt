package br.com.ryuukossei.gymprogress.ui.workouts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.ryuukossei.gymprogress.R
import br.com.ryuukossei.gymprogress.data.repository.GymRepository.PlannedValues
import br.com.ryuukossei.gymprogress.ui.common.NumberStepper
import br.com.ryuukossei.gymprogress.ui.common.normalizeForSearch
import br.com.ryuukossei.gymprogress.ui.exercises.ExerciseItem

/* Formulários de treino e de planejamento, usados nas abas Treinos e no detalhe do treino. */

/** Valores padrão ao adicionar um exercício ao treino (os mesmos do app web). */
val DEFAULT_PLANNED = PlannedValues(sets = 4, repetitions = 10, weight = 0.0, restTimeSeconds = 90, notes = null)

/**
 * Criar/editar treino. [categories]: categorias já usadas, oferecidas como atalho.
 * [onSave] recebe um callback para mostrar o erro, se houver.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutEditorDialog(
    title: String,
    initialName: String,
    initialCategory: String,
    initialComment: String,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, category: String, comment: String, onError: (String) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var category by remember { mutableStateOf(initialCategory) }
    var comment by remember { mutableStateOf(initialComment) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100); error = null },
                    label = { Text("Nome *") },
                    placeholder = { Text("Ex.: Treino A — Peito/Tríceps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it.take(100); error = null },
                    label = { Text("Categoria *") },
                    placeholder = { Text("Ex.: Hipertrofia, Força…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = categories.filter { !it.equals(category.trim(), ignoreCase = true) }
                if (suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { c ->
                            SuggestionChip(onClick = { category = c; error = null }, label = { Text(c) })
                        }
                    }
                }
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it.take(500); error = null },
                    label = { Text("Observação") },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                onSave(name, category, comment) { message -> error = message; saving = false }
            }) { Text(if (saving) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Campos de séries, repetições, carga, descanso e observação. */
@Composable
fun PlannedFields(
    sets: Int, onSets: (Int) -> Unit,
    reps: Int, onReps: (Int) -> Unit,
    weight: Double, onWeight: (Double) -> Unit,
    rest: Int, onRest: (Int) -> Unit,
    notes: String, onNotes: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledStepper("Séries", Modifier.weight(1f)) {
                NumberStepper(sets.toDouble(), { onSets(it.toInt().coerceIn(1, 20)) }, step = 1.0, decimal = false)
            }
            LabeledStepper("Repetições", Modifier.weight(1f)) {
                NumberStepper(reps.toDouble(), { onReps(it.toInt().coerceIn(0, 100)) }, step = 1.0, decimal = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledStepper("Carga (kg)", Modifier.weight(1f)) {
                NumberStepper(weight, { onWeight(it.coerceIn(0.0, 1000.0)) }, step = 1.0, decimal = true)
            }
            LabeledStepper("Descanso (s)", Modifier.weight(1f)) {
                NumberStepper(rest.toDouble(), { onRest(it.toInt().coerceIn(0, 3600)) }, step = 15.0, decimal = false)
            }
        }
        OutlinedTextField(
            value = notes,
            onValueChange = { onNotes(it.take(500)) },
            label = { Text("Observação") },
            placeholder = { Text("Cadência, técnica etc.") },
            minLines = 2,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LabeledStepper(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Editar séries/reps/carga/descanso de um exercício já no treino. */
@Composable
fun EditPlannedDialog(
    exerciseName: String,
    initial: PlannedValues,
    onDismiss: () -> Unit,
    onSave: (PlannedValues, onError: (String) -> Unit) -> Unit,
) {
    var sets by remember { mutableIntStateOf(initial.sets) }
    var reps by remember { mutableIntStateOf(initial.repetitions) }
    var weight by remember { mutableDoubleStateOf(initial.weight) }
    var rest by remember { mutableIntStateOf(initial.restTimeSeconds) }
    var notes by remember { mutableStateOf(initial.notes.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(exerciseName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlannedFields(sets, { sets = it }, reps, { reps = it }, weight, { weight = it }, rest, { rest = it }, notes, { notes = it })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                onSave(PlannedValues(sets, reps, weight, rest, notes)) { message -> error = message; saving = false }
            }) { Text(if (saving) "Salvando…" else "Salvar") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Adicionar exercício ao treino, em tela cheia: primeiro escolhe o exercício
 * (busca, ordem alfabética), depois define séries/reps/carga/descanso.
 * [alreadyInWorkout]: exercícios que já estão no treino (só sinalizados, não bloqueados).
 */
@Composable
fun AddPlannedExerciseDialog(
    exercises: List<ExerciseItem>,
    alreadyInWorkout: Set<String>,
    onCreateExercise: () -> Unit,
    /** Exercício recém-criado pelo atalho "Criar exercício", já selecionado. */
    preselectedId: String?,
    onDismiss: () -> Unit,
    onSave: (exerciseId: String, PlannedValues, onError: (String) -> Unit) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember(preselectedId) { mutableStateOf(preselectedId) }
    var sets by remember { mutableIntStateOf(DEFAULT_PLANNED.sets) }
    var reps by remember { mutableIntStateOf(DEFAULT_PLANNED.repetitions) }
    var weight by remember { mutableDoubleStateOf(DEFAULT_PLANNED.weight) }
    var rest by remember { mutableIntStateOf(DEFAULT_PLANNED.restTimeSeconds) }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val selected = exercises.firstOrNull { it.exercise.publicId == selectedId }

    Dialog(
        onDismissRequest = {
            // Voltar no passo 2 retorna para a escolha do exercício.
            if (!saving) {
                if (selected != null) selectedId = null else onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (selected != null) selectedId = null else onDismiss() }) {
                        Icon(
                            painterResource(if (selected != null) R.drawable.ic_back else R.drawable.ic_close),
                            contentDescription = if (selected != null) "Trocar exercício" else "Fechar",
                        )
                    }
                    Text(
                        if (selected == null) "Escolha o exercício" else "Adicionar ao treino",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }

                if (selected == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        placeholder = { Text("Buscar por nome ou músculo") },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Limpar busca")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                    TextButton(onClick = onCreateExercise, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                        Text("  Criar novo exercício")
                    }
                    val needle = normalizeForSearch(query)
                    val filtered = if (needle.isEmpty()) exercises else exercises.filter { item ->
                        normalizeForSearch(item.exercise.name).contains(needle) ||
                            item.muscleNames.any { normalizeForSearch(it).contains(needle) }
                    }
                    if (filtered.isEmpty()) {
                        Text(
                            if (exercises.isEmpty()) "Nenhum exercício cadastrado." else "Nenhum exercício encontrado.",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(filtered, key = { it.exercise.publicId }) { item ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { selectedId = item.exercise.publicId; error = null }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(item.exercise.name, style = MaterialTheme.typography.bodyLarge)
                                val details = buildList {
                                    if (item.muscleNames.isNotEmpty()) add(item.muscleNames.joinToString(" • "))
                                    if (item.exercise.publicId in alreadyInWorkout) add("já está neste treino")
                                }
                                if (details.isNotEmpty()) {
                                    Text(details.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                } else {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            Text(selected.exercise.name, style = MaterialTheme.typography.titleMedium)
                            if (selected.muscleNames.isNotEmpty()) {
                                Text(selected.muscleNames.joinToString(" • ").uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            if (selected.exercise.publicId in alreadyInWorkout) {
                                Text("Este exercício já está no treino; ele será adicionado de novo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        PlannedFields(sets, { sets = it }, reps, { reps = it }, weight, { weight = it }, rest, { rest = it }, notes, { notes = it })
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    Button(
                        enabled = !saving,
                        onClick = {
                            saving = true
                            onSave(selected.exercise.publicId, PlannedValues(sets, reps, weight, rest, notes)) { message ->
                                error = message
                                saving = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                    ) { Text(if (saving) "Adicionando…" else "Adicionar ao treino") }
                }
            }
        }
    }
}
