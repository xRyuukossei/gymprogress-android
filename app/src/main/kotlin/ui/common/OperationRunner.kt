package br.com.ryuukossei.gymprogress.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.ryuukossei.gymprogress.data.remote.ApiException
import br.com.ryuukossei.gymprogress.data.repository.MissingTokenException
import br.com.ryuukossei.gymprogress.data.repository.PendingChangesException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Evita duplo envio e preserva cancelamento, com o mesmo tratamento de falhas nas telas. */
class OperationRunner(private val scope: CoroutineScope) {
    private val _working = MutableStateFlow(false)
    val working = _working.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun dismissError() { _error.value = null }

    fun run(onError: ((String) -> Unit)? = null, block: suspend () -> Unit) {
        if (_working.value) { onError?.invoke("Aguarde a operação em andamento."); return }
        _working.value = true
        _error.value = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = e.userMessage()
                if (onError != null) onError(message) else _error.value = message
            } finally { _working.value = false }
        }
    }
}

fun Exception.userMessage(): String = when (this) {
    is ApiException, is MissingTokenException, is PendingChangesException,
    is IllegalArgumentException, is IllegalStateException -> message ?: "Não foi possível concluir a operação."
    else -> "Não foi possível concluir a operação. Tente novamente."
}

@Composable
fun OperationErrorDialog(operations: OperationRunner) {
    val error by operations.error.collectAsStateWithLifecycle()
    error?.let {
        AlertDialog(onDismissRequest = operations::dismissError, title = { Text("Não foi possível concluir") }, text = { Text(it) },
            confirmButton = {
                OutlinedButton(onClick = operations::dismissError,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) { Text("Voltar") }
            })
    }
}
