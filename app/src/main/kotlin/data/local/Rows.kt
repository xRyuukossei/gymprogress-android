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
