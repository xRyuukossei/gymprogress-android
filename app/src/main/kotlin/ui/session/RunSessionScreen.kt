package br.com.ryuukossei.gymprogress.ui.session

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.ryuukossei.gymprogress.R
import br.com.ryuukossei.gymprogress.ui.common.EmptyMessage
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.NumberStepper
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatDateShort
import br.com.ryuukossei.gymprogress.ui.common.formatNumber
import br.com.ryuukossei.gymprogress.ui.common.formatRest
import br.com.ryuukossei.gymprogress.ui.common.formatWeight
import br.com.ryuukossei.gymprogress.ui.theme.SuccessColor

/** Tela de execução do treino (sessão em andamento). */
@Composable
fun RunSessionScreen(
    sessionId: String,
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
) {
    val container = appContainer()
    val vm: RunSessionViewModel = viewModel(
        key = "run-$sessionId",
        factory = viewModelFactory {
            initializer { RunSessionViewModel(sessionId, container.repository, container.drafts, container.restTimer) }
        },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val currentIndex by vm.currentIndex.collectAsStateWithLifecycle()
    val restBase by vm.restBase.collectAsStateWithLifecycle()
    val timer by vm.timerState.collectAsStateWithLifecycle()

    var showExit by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
    var showFinish by remember { mutableStateOf(false) }

    RequestNotificationPermission()
    BackHandler { showExit = true }

    when (val s = state) {
        RunUiState.Loading -> LoadingBox()
        RunUiState.NotFound -> EmptyMessage("Sessão não encontrada.")
        is RunUiState.Ready -> {
            if (s.session.finishedAt != null) {
                // Sessão já finalizada: a execução não se aplica.
                LaunchedEffect(Unit) { onFinished(sessionId) }
                return
            }
            if (s.exercises.isEmpty()) {
                EmptyMessage("Nenhum exercício planejado neste treino.")
                return
            }
            val index = currentIndex.coerceIn(0, s.exercises.lastIndex)
            val current = s.exercises[index]
            LaunchedEffect(current.planned.publicId) { vm.initRestFor(current.planned.restTimeSeconds) }

            Scaffold(
                contentWindowInsets = WindowInsets(0),
                topBar = {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showExit = true }) {
                            Icon(painterResource(R.drawable.ic_back), contentDescription = "Sair do treino")
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(s.workoutName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "Exercício ${index + 1} de ${s.exercises.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showFinish = true }) { Text("Finalizar", color = SuccessColor) }
                    }
                },
                bottomBar = {
                    Column(
                        Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .navigationBarsPadding()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RestTimerBar(
                            timer = timer,
                            baseSeconds = restBase,
                            onAdjust = vm::adjustRest,
                            onStart = vm::startTimer,
                            onStop = vm::stopTimer,
                        )
                        ExerciseNavigation(
                            index = index,
                            count = s.exercises.size,
                            onSelect = { i -> vm.selectExercise(i, s.exercises[i].planned.restTimeSeconds) },
                        )
                    }
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // A chave por exercício recria os campos ao trocar de exercício:
                    // cada exercício mostra os próprios valores.
                    key(current.planned.publicId) {
                        ExerciseHeader(current)
                        SetsCard(
                            exercise = current,
                            draftFor = vm::draftFor,
                            onDraft = vm::updateDraft,
                            onConfirm = vm::confirmSet,
                        )
                    }
                }
            }

            if (showExit) {
                AlertDialog(
                    onDismissRequest = { showExit = false },
                    title = { Text("Sair do treino?") },
                    text = { Text("O treino continua em andamento e você pode retomá-lo pela aba Treinos.") },
                    confirmButton = { TextButton(onClick = { showExit = false; onExit() }) { Text("Sair") } },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { showExit = false; showCancel = true }) {
                                Text("Cancelar treino", color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { showExit = false }) { Text("Continuar") }
                        }
                    },
                )
            }
            if (showCancel) {
                AlertDialog(
                    onDismissRequest = { showCancel = false },
                    title = { Text("Cancelar treino?") },
                    text = { Text("O treino em andamento e todas as séries registradas nele serão descartados. Ele não ficará no histórico e isso não pode ser desfeito.") },
                    confirmButton = {
                        TextButton(onClick = { showCancel = false; vm.cancel(onExit) }) {
                            Text("Cancelar treino", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = { TextButton(onClick = { showCancel = false }) { Text("Voltar") } },
                )
            }
            if (showFinish) {
                val total = s.totalSavedSets
                AlertDialog(
                    onDismissRequest = { showFinish = false },
                    title = { Text("Finalizar treino?") },
                    text = {
                        Text(
                            (if (total > 0) "Você registrou $total série${if (total == 1) "" else "s"}. " else "Nenhuma série foi registrada ainda. ") +
                                "Séries não confirmadas (✓) não serão salvas. Depois de finalizado, o treino vai para o histórico."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showFinish = false; vm.finish { onFinished(sessionId) } }) { Text("Finalizar") }
                    },
                    dismissButton = { TextButton(onClick = { showFinish = false }) { Text("Continuar treinando") } },
                )
            }
        }
    }
}

@Composable
private fun ExerciseHeader(exercise: RunExercise) {
    val p = exercise.planned
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.exerciseName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (exercise.muscles.isNotEmpty()) {
                Text(
                    exercise.muscles.joinToString(" • ").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Meta", "${p.sets} × ${p.repetitions}", Modifier.weight(1f))
                Stat("Carga sugerida", formatWeight(p.weight), Modifier.weight(1f))
                Stat("Descanso", formatRest(p.restTimeSeconds), Modifier.weight(1f))
            }
            p.notes?.takeIf { it.isNotBlank() }?.let {
                Text("💡 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            exercise.previous?.let {
                Text(
                    "Última execução: ${formatDateShort(it.date)} (valores entre parênteses)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SetsCard(
    exercise: RunExercise,
    draftFor: (String, Int) -> br.com.ryuukossei.gymprogress.data.draft.SetDraft?,
    onDraft: (String, Int, Int?, Double?) -> Unit,
    onConfirm: (String, Int, Int, Double) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#", Modifier.width(28.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("REPS", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("CARGA (KG)", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(48.dp))
            }
            for (setNumber in 1..exercise.planned.sets) {
                key(setNumber) {
                    SetRow(
                        exercise = exercise,
                        setNumber = setNumber,
                        draft = draftFor(exercise.planned.publicId, setNumber),
                        onDraft = onDraft,
                        onConfirm = onConfirm,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetRow(
    exercise: RunExercise,
    setNumber: Int,
    draft: br.com.ryuukossei.gymprogress.data.draft.SetDraft?,
    onDraft: (String, Int, Int?, Double?) -> Unit,
    onConfirm: (String, Int, Int, Double) -> Unit,
) {
    val p = exercise.planned
    val saved = exercise.saved[setNumber]
    val previous = exercise.previous?.sets?.get(setNumber)

    // Prioridade: o que foi digitado (rascunho) > série já confirmada > última execução > planejado.
    val suggestedReps = saved?.performedRepetitions ?: previous?.performedRepetitions ?: p.repetitions
    val suggestedWeight = saved?.performedWeight ?: previous?.performedWeight ?: p.weight
    var reps by remember { mutableIntStateOf(draft?.reps ?: suggestedReps) }
    var weight by remember { mutableDoubleStateOf(draft?.weight ?: suggestedWeight) }
    var repsEdited by remember { mutableStateOf(draft?.reps != null) }
    var weightEdited by remember { mutableStateOf(draft?.weight != null) }

    // A sugestão pode mudar depois (ex.: série confirmada). Só aplica onde o usuário não mexeu.
    LaunchedEffect(suggestedReps) { if (!repsEdited) reps = suggestedReps }
    LaunchedEffect(suggestedWeight) { if (!weightEdited) weight = suggestedWeight }

    val done = saved != null
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (done) SuccessColor.copy(alpha = 0.10f) else Color.Transparent)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) SuccessColor else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$setNumber",
                style = MaterialTheme.typography.labelMedium,
                color = if (done) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ValueColumn(previous = previous?.performedRepetitions?.toString(), modifier = Modifier.weight(1f)) {
            NumberStepper(
                value = reps.toDouble(),
                onValueChange = {
                    reps = it.toInt()
                    repsEdited = true
                    onDraft(p.publicId, setNumber, reps, null)
                },
                step = 1.0,
                decimal = false,
            )
        }
        ValueColumn(previous = previous?.performedWeight?.let(::formatNumber), modifier = Modifier.weight(1f)) {
            NumberStepper(
                value = weight,
                onValueChange = {
                    weight = it
                    weightEdited = true
                    onDraft(p.publicId, setNumber, null, weight)
                },
                step = 1.0,
                decimal = true,
            )
        }
        FilledIconButton(
            onClick = { onConfirm(p.publicId, setNumber, reps, weight) },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (done) SuccessColor else MaterialTheme.colorScheme.primary,
                contentColor = Color.Black,
            ),
        ) {
            Text("✓", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ValueColumn(previous: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
        if (previous != null) {
            Text(
                "($previous)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ExerciseNavigation(index: Int, count: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onSelect(index - 1) }, enabled = index > 0, modifier = Modifier.weight(1f)) { Text("‹ Anterior") }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .height(8.dp)
                        .width(if (i == index) 18.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
        OutlinedButton(onClick = { onSelect(index + 1) }, enabled = index < count - 1, modifier = Modifier.weight(1f)) { Text("Próximo ›") }
    }
}

/** Pede a permissão de notificações (Android 13+), usada pelo cronômetro. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
