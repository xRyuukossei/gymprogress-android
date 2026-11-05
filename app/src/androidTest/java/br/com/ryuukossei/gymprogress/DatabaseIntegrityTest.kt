package br.com.ryuukossei.gymprogress

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.ryuukossei.gymprogress.data.local.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Usa somente banco em memória; não toca nos treinos nem nas credenciais do aparelho. */
@RunWith(AndroidJUnit4::class)
class DatabaseIntegrityTest {
    private lateinit var db: AppDatabase
    private val dao get() = db.gymDao()
    @Before fun prepare() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        dao.upsertExercise(ExerciseEntity("e", "Supino", null))
        dao.upsertWorkout(WorkoutEntity("w", "Treino A", "Força", null, false))
        dao.upsertWorkoutExercise(WorkoutExerciseEntity("p", "w", "e", 3, 12, 20.0, 60, null, 0, null))
        dao.upsertSession(SessionEntity("s", "w", 1L, null, null, true))
        dao.saveSet("s", "p", 1, 12, 20.0, "set")
    }
    @After fun close() { db.close() }
    @Test fun savingSameLogicalSetUpdatesInsteadOfDuplicating() = runBlocking {
        dao.saveSet("s", "p", 1, 10, 22.0, "different-id")
        assertEquals(1, dao.getCounts().sets)
        assertEquals("set", dao.findSet("s", "p", 1)?.publicId)
        assertEquals(10, dao.findSet("s", "p", 1)?.performedRepetitions)
    }
    @Test fun snapshotDetectsEditsEvenWhenPendingCountDoesNotChange() = runBlocking {
        val before = dao.pendingSnapshot()
        val count = dao.countPendingChanges()
        dao.saveSet("s", "p", 1, 10, 20.0, "unused-id")
        assertEquals(count, dao.countPendingChanges())
        assertNotEquals(before, dao.pendingSnapshot())
    }
    @Test fun acknowledgingOldSnapshotDoesNotClearNewEdits() = runBlocking {
        val sent = requireNotNull(dao.findSet("s", "p", 1))
        dao.saveSet("s", "p", 1, 10, 20.0, "unused-id")
        dao.markPushed(emptyList(), emptyList(), emptyList(), emptyList(), listOf(sent),
            emptyList(), "session", emptyList(), "exercise", emptyList(), "set")
        assertTrue(requireNotNull(dao.findSet("s", "p", 1)).pendingSync)
    }
    @Test fun invalidReplacementRollsBackExistingData() = runBlocking {
        val failure = runCatching {
            dao.replaceAll(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
                listOf(SessionEntity("invalid", "missing-workout", 1L, null, null)), emptyList())
        }
        assertTrue(failure.isFailure)
        assertNotNull(dao.getSession("s"))
        assertNotNull(dao.findSet("s", "p", 1))
    }
    @Test fun activeSessionRecordsAreNotCountedAsReadyToSend() = runBlocking {
        assertEquals(0, dao.observeSendableChanges().first())
    }
}
