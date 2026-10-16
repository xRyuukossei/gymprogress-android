package br.com.ryuukossei.gymprogress.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import br.com.ryuukossei.gymprogress.data.local.SessionSummaryRow
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.ui.common.LoadingBox
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.common.isCompletedSession
import br.com.ryuukossei.gymprogress.ui.theme.SuccessColor
import br.com.ryuukossei.gymprogress.ui.theme.WarningColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/*
 * Início: semana atual, contagem da semana e do mês e calendário mensal.
 * Regras iguais às do app web: conta o dia em que o treino foi finalizado, e só
 * treinos com pelo menos 30 minutos ("concluídos"). Semana de segunda a domingo.
 */

data class HomeUiState(
    /** Dias (no fuso do celular) com pelo menos um treino concluído. */
    val completedDays: Set<LocalDate>,
    val inProgress: List<SessionSummaryRow>,
)

class HomeViewModel(private val repository: GymRepository) : ViewModel() {
    val state: StateFlow<HomeUiState?> = repository.observe {
        repository.dao.observeSessionSummaries().map { sessions ->
            val zone = ZoneId.systemDefault()
            HomeUiState(
                completedDays = sessions
                    .filter { isCompletedSession(it.startedAt, it.finishedAt) }
                    .mapNotNull { s -> s.finishedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } }
                    .toSet(),
                inProgress = sessions.filter { it.finishedAt == null },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = repository.notifyDataChanged()
}

private val ptBr: Locale = Locale.forLanguageTag("pt-BR")
private val WEEK_LABELS = listOf("S", "T", "Q", "Q", "S", "S", "D")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenWorkouts: () -> Unit) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container.repository) } })
    val state by vm.state.collectAsStateWithLifecycle()
    // Ao voltar para a aba (ou para o app em outro dia), recalcula "hoje" e refaz a consulta.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Início") }) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val s = state
        if (s == null) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekDays = (0L..6L).map { weekStart.plusDays(it) }
        val thisWeek = weekDays.count { it in s.completedDays }
        val thisMonth = s.completedDays.count { YearMonth.from(it) == YearMonth.from(today) }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { WeekStrip(weekDays, today, s.completedDays) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Esta semana", thisWeek, if (thisWeek == 1) "dia" else "dias", WarningColor, Modifier.weight(1f))
                    StatCard("Este mês", thisMonth, if (thisMonth == 1) "treino" else "treinos", SuccessColor, Modifier.weight(1f))
                }
            }
            item { MonthCalendar(today, s.completedDays) }
            item {
                val inProgress = s.inProgress.firstOrNull()
                Button(
                    onClick = onOpenWorkouts,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = if (inProgress != null) {
                        ButtonDefaults.buttonColors(containerColor = WarningColor, contentColor = Color.Black)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (inProgress != null) "Treino em andamento" else "Meus treinos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (inProgress != null) inProgress.workoutName else "Comece um treino agora",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("›", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(weekDays: List<LocalDate>, today: LocalDate, completed: Set<LocalDate>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "SUA SEMANA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("Seg → Dom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                weekDays.forEachIndexed { i, day ->
                    val done = day in completed
                    val isToday = day == today
                    Column(
                        Modifier
                            .weight(1f)
                            .background(
                                if (done) SuccessColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.background.copy(alpha = if (i >= 5) 0.4f else 0.6f),
                                RoundedCornerShape(10.dp),
                            )
                            .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(10.dp)) else Modifier)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            WEEK_LABELS[i],
                            style = MaterialTheme.typography.labelSmall,
                            color = if (done) SuccessColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (done) SuccessColor else MaterialTheme.colorScheme.onSurface,
                        )
                        Box(
                            Modifier.size(20.dp).background(
                                if (done) SuccessColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (done) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = "Treino concluído", tint = Color.Black, modifier = Modifier.size(14.dp))
                            } else {
                                Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, suffix: String, accent: Color, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    " $suffix",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthCalendar(today: LocalDate, completed: Set<LocalDate>) {
    // Mês exibido, guardado como "aaaa-mm" para sobreviver à rotação da tela.
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthText)
    val monthCount = completed.count { YearMonth.from(it) == month }
    val leading = month.atDay(1).dayOfWeek.value - 1 // segunda = 0
    val cells: List<LocalDate?> = buildList {
        repeat(leading) { add(null) }
        for (d in 1..month.lengthOfMonth()) add(month.atDay(d))
        while (size % 7 != 0) add(null)
    }
    val monthName = month.month.getDisplayName(TextStyle.FULL_STANDALONE, ptBr).replaceFirstChar { it.titlecase(ptBr) }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Mês anterior")
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$monthName ${month.year}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "$monthCount treino${if (monthCount == 1) "" else "s"} concluído${if (monthCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                    // Mesmo ícone de "voltar", espelhado.
                    Icon(
                        painterResource(R.drawable.ic_back),
                        contentDescription = "Próximo mês",
                        modifier = Modifier.graphicsLayer(scaleX = -1f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                WEEK_LABELS.forEach {
                    Text(
                        it,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val done = day in completed
                            Box(
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(
                                        if (done) SuccessColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.background.copy(alpha = 0.4f),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .then(if (day == today) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (done) SuccessColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
