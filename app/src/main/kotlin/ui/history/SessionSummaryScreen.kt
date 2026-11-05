package br.com.ryuukossei.gymprogress.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.ryuukossei.gymprogress.ui.common.EmptyMessage
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.formatDateTime
import br.com.ryuukossei.gymprogress.ui.common.formatDuration

/** Resumo da sessão recém-finalizada, sem estatísticas ou gráficos de evolução. */
@Composable
fun SessionSummaryScreen(sessionId: String, onDetails: () -> Unit, onHome: () -> Unit) {
    val container = appContainer()
    val vm: SessionDetailViewModel = viewModel(key = "summary-$sessionId",
        factory = viewModelFactory { initializer { SessionDetailViewModel(container.repository.dao, sessionId) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state
    val session = s?.session
    when {
        s == null -> LoadingBox()
        session == null -> EmptyMessage("Sessão não encontrada.")
        else -> Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("✓ Treino registrado", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary)
            Text(session.workoutName, style = MaterialTheme.typography.titleLarge)
            Text(formatDateTime(session.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Duração: ${formatDuration(session.startedAt, session.finishedAt)}")
                    Text("${session.setCount} séries registradas")
                    Text("${s.exercises.size} exercícios com séries registradas")
                }
            }
            Text(if (session.pendingSync) "Envio ao servidor pendente. Os dados estão salvos neste celular."
                else "Treino sincronizado com o servidor.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onDetails, modifier = Modifier.fillMaxWidth()) { Text("Ver detalhes no histórico") }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Voltar aos treinos") }
        }
    }
}
