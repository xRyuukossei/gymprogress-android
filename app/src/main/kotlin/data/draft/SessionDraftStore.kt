package br.com.ryuukossei.gymprogress.data.draft

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SetDraft(val weight: Double? = null, val reps: Int? = null)

@Serializable
data class SessionDraft(
    val currentIndex: Int = 0,
    /** chave: "<workoutExercisePublicId>:<número da série>" */
    val sets: Map<String, SetDraft> = emptyMap(),
)

/**
 * Rascunho de uma sessão em andamento: o que foi digitado e ainda não confirmado (✓)
 * em cada série, e o exercício que estava aberto. Sobrevive a sair da tela, fechar o
 * app ou reiniciar o celular. É apagado ao finalizar ou cancelar o treino.
 */
class SessionDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("gymprogress_drafts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(sessionId: String): SessionDraft {
        val raw = prefs.getString(sessionId, null) ?: return SessionDraft()
        return try {
            json.decodeFromString(SessionDraft.serializer(), raw)
        } catch (e: Exception) {
            SessionDraft()
        }
    }

    fun saveCurrentIndex(sessionId: String, index: Int) =
        write(sessionId, load(sessionId).copy(currentIndex = index))

    fun saveSet(sessionId: String, workoutExerciseId: String, setNumber: Int, weight: Double? = null, reps: Int? = null) {
        val draft = load(sessionId)
        val key = key(workoutExerciseId, setNumber)
        val current = draft.sets[key] ?: SetDraft()
        val updated = current.copy(weight = weight ?: current.weight, reps = reps ?: current.reps)
        write(sessionId, draft.copy(sets = draft.sets + (key to updated)))
    }

    fun clear(sessionId: String) = prefs.edit { remove(sessionId) }

    private fun write(sessionId: String, draft: SessionDraft) =
        prefs.edit { putString(sessionId, json.encodeToString(SessionDraft.serializer(), draft)) }

    companion object {
        fun key(workoutExerciseId: String, setNumber: Int) = "$workoutExerciseId:$setNumber"
    }
}
