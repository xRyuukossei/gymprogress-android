package br.com.ryuukossei.gymprogress.data.remote

import java.time.Instant
import java.util.Locale

private val publicIdPattern = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
internal fun canonicalPublicId(value: String): String {
    require(publicIdPattern.matches(value)) { "Identificador inválido na resposta do servidor." }
    return value.lowercase(Locale.ROOT)
}

/** Valida o contrato de exportação antes de substituir dados locais.
 * O servidor identifica séries pelo PublicId, não por (sessão, exercício, número).
 * O histórico pode conter números repetidos e referências a planejamento de outro treino.
 * As regras mais estritas para novos registros são aplicadas no repositório, não na leitura.
 */
internal fun validateSyncExport(export: SyncExportDto) {
    fun <T> unique(items: List<T>, id: (T) -> String): Map<String, T> {
        val mapped = items.associateBy { canonicalPublicId(id(it)) }
        require(mapped.size == items.size) { "Identificadores duplicados na resposta do servidor." }
        return mapped
    }
    Instant.parse(export.exportedAt)
    val muscles = export.targetMuscles.map { it.id }.toSet()
    require(muscles.size == export.targetMuscles.size && muscles.all { it > 0 }) { "Grupos musculares inválidos." }
    val exercises = unique(export.exercises) { it.publicId }
    val workouts = unique(export.workouts) { it.publicId }
    val planned = unique(export.workoutExercises) { it.publicId }
    val sessions = unique(export.sessions) { it.publicId }
    unique(export.sets) { it.publicId }
    exercises.values.forEach { require(it.targetMuscleIds.all { id -> id in muscles }) { "Grupo muscular não encontrado." } }
    planned.values.forEach {
        require(canonicalPublicId(it.workoutPublicId) in workouts && canonicalPublicId(it.exercisePublicId) in exercises) { "Planejamento com referências inválidas." }
        require(it.sets in 1..20 && it.repetitions in 0..100 && it.weight.isFinite() && it.weight in 0.0..1000.0 &&
            it.restTimeSeconds in 0..3600) { "Valores inválidos no planejamento recebido." }
        it.removedAt?.let(Instant::parse)
    }
    sessions.values.forEach {
        require(canonicalPublicId(it.workoutPublicId) in workouts) { "Treino da sessão não encontrado." }
        Instant.parse(it.startedAt)
        it.finishedAt?.let(Instant::parse)
    }
    export.sets.forEach {
        val sessionId = canonicalPublicId(it.sessionPublicId)
        val plannedId = canonicalPublicId(it.workoutExercisePublicId)
        require(sessionId in sessions) { "Sessão da série não encontrada." }
        require(plannedId in planned) { "Exercício da série não encontrado." }
        require(it.setNumber in 1..20 && it.performedRepetitions in 0..100 && it.performedWeight.isFinite() &&
            it.performedWeight in 0.0..1000.0) { "Valores inválidos na série recebida." }
    }
}
