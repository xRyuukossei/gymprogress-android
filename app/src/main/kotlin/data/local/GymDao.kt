package br.com.ryuukossei.gymprogress.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GymDao {

    // ===== Leitura (telas) =====

    @Query("SELECT * FROM workouts")
    fun observeWorkouts(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE publicId = :workoutId")
    fun observeWorkout(workoutId: String): Flow<WorkoutEntity?>

    @Query(
        """
        SELECT we.publicId, we.exercisePublicId, e.name AS exerciseName, we.sets, we.repetitions,
               we.weight, we.restTimeSeconds, we.notes, we.sortOrder
        FROM workout_exercises we
        JOIN exercises e ON e.publicId = we.exercisePublicId
        WHERE we.workoutPublicId = :workoutId AND we.removedAt IS NULL
        ORDER BY we.sortOrder
        """
    )
    fun observePlannedExercises(workoutId: String): Flow<List<PlannedExerciseRow>>

    @Query(
        """
        SELECT etm.exercisePublicId, tm.name AS muscleName
        FROM exercise_target_muscles etm
        JOIN target_muscles tm ON tm.id = etm.targetMuscleId
        ORDER BY tm.id
        """
    )
    fun observeExerciseMuscles(): Flow<List<ExerciseMuscleRow>>

    @Query(
        """
        SELECT s.publicId, s.workoutPublicId, w.name AS workoutName, s.startedAt, s.finishedAt,
               (SELECT COUNT(*) FROM session_sets x WHERE x.sessionPublicId = s.publicId) AS setCount
        FROM sessions s
        JOIN workouts w ON w.publicId = s.workoutPublicId
        ORDER BY s.startedAt DESC
        """
    )
    fun observeSessionSummaries(): Flow<List<SessionSummaryRow>>

    @Query(
        """
        SELECT s.publicId, s.workoutPublicId, w.name AS workoutName, s.startedAt, s.finishedAt,
               (SELECT COUNT(*) FROM session_sets x WHERE x.sessionPublicId = s.publicId) AS setCount
        FROM sessions s
        JOIN workouts w ON w.publicId = s.workoutPublicId
        WHERE s.publicId = :sessionId
        """
    )
    fun observeSessionSummary(sessionId: String): Flow<SessionSummaryRow?>

    @Query(
        """
        SELECT x.publicId, x.workoutExercisePublicId, e.name AS exerciseName, we.sortOrder AS exerciseOrder,
               x.setNumber, x.performedRepetitions, x.performedWeight
        FROM session_sets x
        JOIN workout_exercises we ON we.publicId = x.workoutExercisePublicId
        JOIN exercises e ON e.publicId = we.exercisePublicId
        WHERE x.sessionPublicId = :sessionId
        ORDER BY we.sortOrder, x.workoutExercisePublicId, x.setNumber
        """
    )
    fun observeSessionSets(sessionId: String): Flow<List<SessionSetRow>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM exercises) AS exercises,
            (SELECT COUNT(*) FROM workouts) AS workouts,
            (SELECT COUNT(*) FROM sessions) AS sessions,
            (SELECT COUNT(*) FROM session_sets) AS sets
        """
    )
    fun observeCounts(): Flow<LocalCounts>

    // ===== Execução do treino =====

    @Query("SELECT * FROM sessions WHERE publicId = :sessionId")
    fun observeSession(sessionId: String): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE publicId = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Query("SELECT * FROM session_sets WHERE sessionPublicId = :sessionId ORDER BY setNumber")
    fun observeSetsOfSession(sessionId: String): Flow<List<SessionSetEntity>>

    @Query(
        """
        SELECT * FROM session_sets
        WHERE sessionPublicId = :sessionId AND workoutExercisePublicId = :workoutExerciseId AND setNumber = :setNumber
        LIMIT 1
        """
    )
    suspend fun findSet(sessionId: String, workoutExerciseId: String, setNumber: Int): SessionSetEntity?

    /** Séries de sessões finalizadas para os exercícios informados (mais recentes primeiro). */
    @Query(
        """
        SELECT we.exercisePublicId, s.publicId AS sessionPublicId, s.startedAt,
               x.setNumber, x.performedRepetitions, x.performedWeight
        FROM session_sets x
        JOIN sessions s ON s.publicId = x.sessionPublicId
        JOIN workout_exercises we ON we.publicId = x.workoutExercisePublicId
        WHERE s.finishedAt IS NOT NULL AND s.publicId != :excludeSessionId
        ORDER BY s.startedAt DESC, x.setNumber
        """
    )
    fun observePreviousSets(excludeSessionId: String): Flow<List<PreviousSetRow>>

    @Query("SELECT * FROM sessions WHERE finishedAt IS NULL AND workoutPublicId = :workoutId ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgressSessionOfWorkout(workoutId: String): Flow<SessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSession(session: SessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSet(set: SessionSetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPendingDeletion(item: PendingDeletionEntity)

    @Query("DELETE FROM session_sets WHERE sessionPublicId = :sessionId") suspend fun deleteSetsOfSession(sessionId: String)
    @Query("DELETE FROM sessions WHERE publicId = :sessionId") suspend fun deleteSession(sessionId: String)

    // ===== Pendências de sincronização =====

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM exercises WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM workouts WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM workout_exercises WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM sessions WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM session_sets WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM pending_deletions)
        """
    )
    suspend fun countPendingChanges(): Int

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM exercises WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM workouts WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM workout_exercises WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM sessions WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM session_sets WHERE pendingSync = 1) +
            (SELECT COUNT(*) FROM pending_deletions)
        """
    )
    fun observePendingChanges(): Flow<Int>

    // ===== Importação completa (substitui tudo) =====

    @Query("DELETE FROM session_sets") suspend fun deleteAllSets()
    @Query("DELETE FROM sessions") suspend fun deleteAllSessions()
    @Query("DELETE FROM workout_exercises") suspend fun deleteAllWorkoutExercises()
    @Query("DELETE FROM workouts") suspend fun deleteAllWorkouts()
    @Query("DELETE FROM exercise_target_muscles") suspend fun deleteAllExerciseMuscles()
    @Query("DELETE FROM exercises") suspend fun deleteAllExercises()
    @Query("DELETE FROM target_muscles") suspend fun deleteAllTargetMuscles()
    @Query("DELETE FROM pending_deletions") suspend fun deleteAllPendingDeletions()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTargetMuscles(items: List<TargetMuscleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExercises(items: List<ExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExerciseMuscles(items: List<ExerciseTargetMuscleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWorkouts(items: List<WorkoutEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertWorkoutExercises(items: List<WorkoutExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSessions(items: List<SessionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSets(items: List<SessionSetEntity>)
}
