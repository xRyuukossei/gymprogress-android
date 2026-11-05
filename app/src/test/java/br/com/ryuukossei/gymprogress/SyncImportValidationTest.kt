package br.com.ryuukossei.gymprogress

import br.com.ryuukossei.gymprogress.data.remote.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class SyncImportValidationTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12, '0')}"
    private fun valid() = SyncExportDto(
        exportedAt = "2026-11-04T12:00:00Z",
        targetMuscles = listOf(SyncTargetMuscleDto(1, "Peitoral")),
        exercises = listOf(SyncExerciseDto(id(1), "Supino", targetMuscleIds = listOf(1))),
        workouts = listOf(SyncWorkoutDto(id(2), "Treino A", "Força")),
        workoutExercises = listOf(SyncWorkoutExerciseDto(id(3), id(2), id(1), 3, 12, 20.0, 60, order = 0)),
        sessions = listOf(SyncSessionDto(id(4), id(2), "2026-11-04T10:00:00Z", "2026-11-04T11:00:00Z")),
        sets = listOf(SyncSetDto(id(5), id(4), id(3), 1, 12, 20.0)),
    )
    @Test fun rejectsIncompleteExportInsteadOfTreatingMissingCollectionsAsEmpty() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(SyncExportDto.serializer(), """{"exportedAt":"2026-11-04T12:00:00Z"}""")
        }
    }
    @Test fun acceptsValidExportAndHistoricalSetsBeyondCurrentPlan() {
        val value = valid()
        validateSyncExport(value)
        validateSyncExport(value.copy(workoutExercises = value.workoutExercises.map { it.copy(sets = 1) },
            sets = value.sets.map { it.copy(setNumber = 3) }))
    }
    @Test fun preservesRepeatedSetNumbersWithDistinctPublicIdsButRejectsRepeatedPublicIds() {
        val value = valid()
        val repeatedNumber = value.copy(sets = value.sets + value.sets.first().copy(publicId = id(6)))
        validateSyncExport(repeatedNumber)
        assertEquals(2, repeatedNumber.sets.size)
        assertThrows(IllegalArgumentException::class.java) { validateSyncExport(value.copy(exercises = value.exercises + value.exercises)) }
    }
    @Test fun rejectsMissingReferencesAndNonFiniteWeights() {
        val value = valid()
        assertThrows(IllegalArgumentException::class.java) { validateSyncExport(value.copy(exercises = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { validateSyncExport(value.copy(sets = value.sets.map { it.copy(performedWeight = Double.NaN) })) }
    }
    @Test fun acceptsHistoricalReferencesToPlanningFromAnotherWorkout() {
        val value = valid()
        validateSyncExport(value.copy(workouts = value.workouts + SyncWorkoutDto(id(7), "Treino B", "Força"),
            sessions = value.sessions.map { it.copy(workoutPublicId = id(7)) }))
    }
    @Test fun acceptsHistoricalOrderingAndDatesSupportedByServerContract() {
        val value = valid()
        validateSyncExport(value.copy(workoutExercises = value.workoutExercises.map { it.copy(order = -1) },
            sessions = value.sessions.map { it.copy(finishedAt = "2026-11-03T10:00:00Z") }))
    }
    @Test fun rejectsRouteInjectionAndMalformedSessionDates() {
        assertThrows(IllegalArgumentException::class.java) { canonicalPublicId("../workouts") }
        val value = valid()
        assertThrows(java.time.format.DateTimeParseException::class.java) {
            validateSyncExport(value.copy(sessions = value.sessions.map { it.copy(finishedAt = "not-a-date") }))
        }
    }
}
