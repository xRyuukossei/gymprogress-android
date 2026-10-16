package br.com.ryuukossei.gymprogress.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.ryuukossei.gymprogress.data.draft.SessionDraftStore
import br.com.ryuukossei.gymprogress.data.draft.SetDraft
import br.com.ryuukossei.gymprogress.data.local.PlannedExerciseRow
import br.com.ryuukossei.gymprogress.data.local.PreviousSetRow
import br.com.ryuukossei.gymprogress.data.local.SessionEntity
import br.com.ryuukossei.gymprogress.data.local.SessionSetEntity
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.timer.RestTimer
import br.com.ryuukossei.gymprogress.timer.RestTimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Última execução do mesmo exercício (sessão finalizada mais recente). */
data class PreviousExecution(val date: Long, val sets: Map<Int, PreviousSetRow>)

data class RunExercise(
    val planned: PlannedExerciseRow,
    val muscles: List<String>,
    /** Séries já confirmadas nesta sessão, por número da série. */
    val saved: Map<Int, SessionSetEntity>,
    val previous: PreviousExecution?,
)

sealed interface RunUiState {
    data object Loading : RunUiState
    data object NotFound : RunUiState
    data class Ready(
        val session: SessionEntity,
        val workoutName: String,
        val exercises: List<RunExercise>,
        val totalSavedSets: Int,
    ) : RunUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class RunSessionViewModel(
    private val sessionId: String,
    private val repository: GymRepository,
    private val drafts: SessionDraftStore,
    private val timer: RestTimer,
) : ViewModel() {

    private val dao = repository.dao
    private var draft = drafts.load(sessionId)

    private val _currentIndex = MutableStateFlow(draft.currentIndex)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** Duração do descanso a usar no próximo início (ajustável com −5/+5). */
    private val _restBase = MutableStateFlow(60)
    val restBase: StateFlow<Int> = _restBase.asStateFlow()

    val timerState: StateFlow<RestTimerState?> = timer.state

    val state: StateFlow<RunUiState> =
        dao.observeSession(sessionId).flatMapLatest { session ->
            if (session == null) {
                flowOf<RunUiState>(RunUiState.NotFound)
            } else {
                combine(
                    dao.observeWorkout(session.workoutPublicId),
                    dao.observePlannedExercises(session.workoutPublicId),
                    dao.observeExerciseMuscles(),
                    dao.observeSetsOfSession(sessionId),
                    dao.observePreviousSets(sessionId),
                ) { workout, planned, muscles, sets, previous ->
                    val musclesByExercise = muscles.groupBy({ it.exercisePublicId }, { it.muscleName })
                    val savedByExercise = sets.groupBy { it.workoutExercisePublicId }
                    val previousByExercise = latestExecutionByExercise(previous)
                    RunUiState.Ready(
                        session = session,
                        workoutName = workout?.name ?: "Treino",
                        exercises = planned.map { p ->
                            RunExercise(
                                planned = p,
                                muscles = musclesByExercise[p.exercisePublicId].orEmpty(),
                                saved = savedByExercise[p.publicId].orEmpty().associateBy { it.setNumber },
                                previous = previousByExercise[p.exercisePublicId],
                            )
                        },
                        totalSavedSets = sets.size,
                    )
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RunUiState.Loading)

    fun draftFor(workoutExerciseId: String, setNumber: Int): SetDraft? =
        draft.sets[SessionDraftStore.key(workoutExerciseId, setNumber)]

    fun selectExercise(index: Int, restSeconds: Int) {
        _currentIndex.value = index
        draft = draft.copy(currentIndex = index)
        drafts.saveCurrentIndex(sessionId, index)
        _restBase.value = defaultRest(restSeconds)
    }

    /** Ajusta o descanso padrão ao abrir um exercício (sem mexer em um cronômetro já correndo). */
    fun initRestFor(restSeconds: Int) {
        _restBase.value = defaultRest(restSeconds)
    }

    fun adjustRest(deltaSeconds: Int) {
        _restBase.value = (_restBase.value + deltaSeconds).coerceIn(5, 3600)
    }

    fun updateDraft(workoutExerciseId: String, setNumber: Int, reps: Int? = null, weight: Double? = null) {
        val key = SessionDraftStore.key(workoutExerciseId, setNumber)
        val current = draft.sets[key] ?: SetDraft()
        draft = draft.copy(sets = draft.sets + (key to current.copy(reps = reps ?: current.reps, weight = weight ?: current.weight)))
        drafts.saveSet(sessionId, workoutExerciseId, setNumber, weight = weight, reps = reps)
    }

    /** Confirma a série (grava no banco local) e inicia o descanso. */
    fun confirmSet(workoutExerciseId: String, setNumber: Int, reps: Int, weight: Double) {
        viewModelScope.launch {
            repository.saveSet(sessionId, workoutExerciseId, setNumber, reps, weight)
            timer.start(_restBase.value, sessionId)
        }
    }

    fun startTimer() = timer.start(_restBase.value, sessionId)

    fun stopTimer() = timer.stop()

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.finishSession(sessionId)
            cleanUp()
            onDone()
        }
    }

    fun cancel(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.cancelSession(sessionId)
            cleanUp()
            onDone()
        }
    }

    private fun cleanUp() {
        drafts.clear(sessionId)
        if (timer.state.value?.sessionId == sessionId) timer.stop()
    }

    private fun defaultRest(restSeconds: Int) = if (restSeconds > 0) restSeconds else 60

    private fun latestExecutionByExercise(rows: List<PreviousSetRow>): Map<String, PreviousExecution> {
        // As linhas vêm da sessão mais recente para a mais antiga; fica a primeira sessão de cada exercício.
        val result = mutableMapOf<String, PreviousExecution>()
        rows.groupBy { it.exercisePublicId }.forEach { (exerciseId, list) ->
            val latestSession = list.first().sessionPublicId
            val sets = list.filter { it.sessionPublicId == latestSession }
            result[exerciseId] = PreviousExecution(sets.first().startedAt, sets.associateBy { it.setNumber })
        }
        return result
    }
}
