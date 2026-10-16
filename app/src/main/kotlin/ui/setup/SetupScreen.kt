package br.com.ryuukossei.gymprogress.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.ryuukossei.gymprogress.data.repository.GymRepository
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val baseUrl: String,
    val token: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class SetupViewModel(private val repository: GymRepository, initialUrl: String) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState(baseUrl = initialUrl))
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onBaseUrlChange(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun onTokenChange(value: String) = _state.update { it.copy(token = value, error = null) }

    /** Valida o token baixando os dados. Em caso de sucesso, o app passa para as telas principais. */
    fun connect() {
        val current = _state.value
        if (current.baseUrl.isBlank() || current.token.isBlank()) {
            _state.update { it.copy(error = "Informe o endereço da API e o token.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                repository.connectAndImport(current.baseUrl, current.token)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Erro desconhecido.") }
            }
        }
    }
}

@Composable
fun SetupScreen() {
    val container = appContainer()
    val vm: SetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SetupViewModel(container.repository, container.settings.state.value.baseUrl) }
        }
    )
    val state by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("GymProgress", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            "Conecte o app ao seu servidor. Seus treinos e todo o histórico serão copiados para este celular.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = vm::onBaseUrlChange,
            label = { Text("Endereço da API") },
            singleLine = true,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.token,
            onValueChange = vm::onTokenChange,
            label = { Text("Token do aparelho") },
            placeholder = { Text("gp_…") },
            singleLine = true,
            enabled = !state.loading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "O token é criado no servidor com o comando \"device-token create\" e fica guardado criptografado neste celular.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = vm::connect,
            enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Text("Importando dados…")
                }
            } else {
                Text("Conectar e importar")
            }
        }
    }
}
