package br.com.ryuukossei.gymprogress.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import br.com.ryuukossei.gymprogress.data.local.SessionSetRow
import br.com.ryuukossei.gymprogress.data.local.SessionSummaryRow
import br.com.ryuukossei.gymprogress.ui.common.EmptyMessage
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatDateTime
import br.com.ryuukossei.gymprogress.ui.common.formatDuration
import br.com.ryuukossei.gymprogress.ui.common.formatNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Séries de um exercício dentro da sessão. */
data class ExerciseSets(
    val workoutExercisePublicId: String,
    val exerciseName: String,
    val sets: List<SessionSetRow>,
)

data class SessionDetailUiState(
    val session: SessionSummaryRow?,
    val exercises: List<ExerciseSets>,
)

class SessionDetailViewModel(dao: GymDao, sessionId: String) : ViewModel() {
    val state: StateFlow<SessionDetailUiState?> =
        combine(dao.observeSessionSummary(sessionId), dao.observeSessionSets(sessionId)) { session, sets ->
            // A consulta já vem ordenada pela ordem do exercício no treino e pelo número da série.
            val grouped = sets
                .groupBy { it.workoutExercisePublicId }
                .map { (id, rows) -> ExerciseSets(id, rows.first().exerciseName, rows) }
            SessionDetailUiState(session, grouped)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(sessionId: String, onBack: () -> Unit) {
    val container = appContainer()
    val vm: SessionDetailViewModel = viewModel(
        key = "session-$sessionId",
        factory = viewModelFactory { initializer { SessionDetailViewModel(container.repository.dao, sessionId) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.session?.workoutName ?: "Sessão", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Voltar")
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val s = state
        val session = s?.session
        when {
            s == null -> LoadingBox(Modifier.padding(padding))
            session == null -> EmptyMessage("Sessão não encontrada.", Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "${formatDateTime(session.startedAt)} • ${formatDuration(session.startedAt, session.finishedAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.exercises.isEmpty()) {
                    item { Text("Nenhuma série registrada.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(s.exercises, key = { it.workoutExercisePublicId }) { ExerciseSetsCard(it) }
            }
        }
    }
}

@Composable
private fun ExerciseSetsCard(item: ExerciseSets) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.exerciseName, style = MaterialTheme.typography.titleMedium)
            item.sets.forEach { set ->
                Row {
                    Text(
                        "Série ${set.setNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(80.dp),
                    )
                    Text(
                        "${set.performedRepetitions} reps × ${formatNumber(set.performedWeight)} kg",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
