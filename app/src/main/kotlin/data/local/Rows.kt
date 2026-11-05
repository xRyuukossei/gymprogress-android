package br.com.ryuukossei.gymprogress.data.local

/* Resultados de consultas com junções, usados pelas telas. */

data class PlannedExerciseRow(
    val publicId: String,
    val exercisePublicId: String,
    val exerciseName: String,
    val sets: Int,
    val repetitions: Int,
    val weight: Double,
    val restTimeSeconds: Int,
    val notes: String?,
    val sortOrder: Int,
)

data class ExerciseMuscleRow(
    val exercisePublicId: String,
    val muscleName: String,
)

data class SessionSummaryRow(
    val publicId: String,
    val workoutPublicId: String,
    val workoutName: String,
    val startedAt: Long,
    val finishedAt: Long?,
    /** true enquanto a sessão (ou alteração nela) ainda não foi enviada ao servidor. */
    val pendingSync: Boolean,
    val setCount: Int,
)

data class SessionSetRow(
    val publicId: String,
    val workoutExercisePublicId: String,
    val exerciseName: String,
    val exerciseOrder: Int,
    val setNumber: Int,
    val performedRepetitions: Int,
    val performedWeight: Double,
)

data class LocalCounts(
    val exercises: Int,
    val workouts: Int,
    val sessions: Int,
    val sets: Int,
)

data class PreviousSetRow(
    val exercisePublicId: String,
    val sessionPublicId: String,
    val startedAt: Long,
    val setNumber: Int,
    val performedRepetitions: Int,
    val performedWeight: Double,
)

data class ExerciseUsageRow(
    val exercisePublicId: String,
    val workoutCount: Int,
)

/** Resumo do planejamento atual, sem contar exercícios removidos. */
data class WorkoutOverviewRow(
    val workoutPublicId: String,
    val exerciseCount: Int,
    val plannedSets: Int,
)

/** Snapshot determinístico para detectar mudanças locais durante uma transferência. */
data class PendingChangesSnapshot(
    val exercises: List<ExerciseEntity>,
    val muscles: List<ExerciseTargetMuscleEntity>,
    val workouts: List<WorkoutEntity>,
    val workoutExercises: List<WorkoutExerciseEntity>,
    val sessions: List<SessionEntity>,
    val sets: List<SessionSetEntity>,
    val setParents: List<SessionEntity>,
    val deletions: List<PendingDeletionEntity>,
) {
    val isEmpty: Boolean get() = exercises.isEmpty() && workouts.isEmpty() && workoutExercises.isEmpty() &&
        sessions.isEmpty() && sets.isEmpty() && deletions.isEmpty()
}
