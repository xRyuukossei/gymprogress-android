package br.com.ryuukossei.gymprogress.data.remote

import kotlinx.serialization.Serializable

/*
 * Formato JSON dos endpoints /api/sync da API (nomes em camelCase, como o ASP.NET gera).
 * Datas chegam como texto ISO-8601 em UTC (ex.: "2026-10-16T13:05:00.123Z").
 */

@Serializable
data class SyncExportDto(
    val exportedAt: String,
    val targetMuscles: List<SyncTargetMuscleDto> = emptyList(),
    val exercises: List<SyncExerciseDto> = emptyList(),
    val workouts: List<SyncWorkoutDto> = emptyList(),
    val workoutExercises: List<SyncWorkoutExerciseDto> = emptyList(),
    val sessions: List<SyncSessionDto> = emptyList(),
    val sets: List<SyncSetDto> = emptyList(),
)

@Serializable
data class SyncTargetMuscleDto(
    val id: Int,
    val name: String,
)

@Serializable
data class SyncExerciseDto(
    val publicId: String,
    val name: String,
    val comment: String? = null,
    val targetMuscleIds: List<Int> = emptyList(),
)

@Serializable
data class SyncWorkoutDto(
    val publicId: String,
    val name: String,
    val category: String,
    val comment: String? = null,
    val isArchived: Boolean = false,
)

@Serializable
data class SyncWorkoutExerciseDto(
    val publicId: String,
    val workoutPublicId: String,
    val exercisePublicId: String,
    val sets: Int,
    val repetitions: Int,
    val weight: Double,
    val restTimeSeconds: Int,
    val notes: String? = null,
    val order: Int,
    val removedAt: String? = null,
)

@Serializable
data class SyncSessionDto(
    val publicId: String,
    val workoutPublicId: String,
    val startedAt: String,
    val finishedAt: String? = null,
    val notes: String? = null,
)

@Serializable
data class SyncSetDto(
    val publicId: String,
    val sessionPublicId: String,
    val workoutExercisePublicId: String,
    val setNumber: Int,
    val performedRepetitions: Int,
    val performedWeight: Double,
)
