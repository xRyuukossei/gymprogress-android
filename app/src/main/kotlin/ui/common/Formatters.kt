package br.com.ryuukossei.gymprogress.ui.common

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/* Formatação para exibição (padrão brasileiro, fuso do celular). */

private val ptBr: Locale = Locale.forLanguageTag("pt-BR")
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", ptBr)
private val dateShortFormat = DateTimeFormatter.ofPattern("dd MMM", ptBr)

/** Sessão finalizada com pelo menos 30 minutos conta como treino concluído (mesma regra do app web). */
const val COMPLETED_MIN_DURATION_MS = 30 * 60 * 1000L

fun isCompletedSession(startedAt: Long, finishedAt: Long?): Boolean =
    finishedAt != null && finishedAt - startedAt >= COMPLETED_MIN_DURATION_MS

fun formatDateTime(epochMillis: Long): String =
    dateTimeFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatDateShort(epochMillis: Long): String =
    dateShortFormat.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

fun formatDuration(startedAt: Long, finishedAt: Long?): String {
    if (finishedAt == null) return "Em andamento"
    val totalMin = ((finishedAt - startedAt) / 60_000L).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}

fun formatWeight(weight: Double): String {
    if (weight == 0.0) return "—"
    val nf = NumberFormat.getNumberInstance(ptBr).apply { maximumFractionDigits = 2 }
    return "${nf.format(weight)} kg"
}

fun formatNumber(value: Double): String =
    NumberFormat.getNumberInstance(ptBr).apply { maximumFractionDigits = 2 }.format(value)

fun formatRest(seconds: Int): String = when {
    seconds <= 0 -> "—"
    seconds < 60 -> "${seconds}s"
    seconds % 60 == 0 -> "${seconds / 60}min"
    else -> "${seconds / 60}m ${seconds % 60}s"
}
