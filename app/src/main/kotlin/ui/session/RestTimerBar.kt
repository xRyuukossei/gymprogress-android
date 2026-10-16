package br.com.ryuukossei.gymprogress.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.ryuukossei.gymprogress.timer.RestTimerState
import br.com.ryuukossei.gymprogress.ui.theme.WarningColor
import kotlinx.coroutines.delay

/**
 * Barra do cronômetro de descanso. A contagem é calculada a partir do horário de
 * término guardado no [br.com.ryuukossei.gymprogress.timer.RestTimer], então continua
 * certa mesmo depois de bloquear a tela ou sair do app.
 */
@Composable
fun RestTimerBar(
    timer: RestTimerState?,
    baseSeconds: Int,
    onAdjust: (Int) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = timer != null && timer.endAt > now
    LaunchedEffect(timer) {
        while (timer != null) {
            now = System.currentTimeMillis()
            if (now >= timer.endAt) break
            delay(250)
        }
        now = System.currentTimeMillis()
    }

    val seconds = if (running) (((timer!!.endAt - now) + 999) / 1000).toInt() else baseSeconds
    val ending = running && seconds <= 5

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ending) WarningColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { onAdjust(-STEP) }, enabled = !running, modifier = Modifier.size(48.dp)) { Text("−5") }
            Text(
                "%d:%02d".format(seconds / 60, seconds % 60),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = if (ending) WarningColor else MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = { onAdjust(STEP) }, enabled = !running, modifier = Modifier.size(48.dp)) { Text("+5") }
            if (running) {
                OutlinedButton(onClick = onStop) { Text("Parar") }
            } else {
                Button(onClick = onStart) { Text("Iniciar") }
            }
        }
    }
}

private const val STEP = 5
