package br.com.ryuukossei.gymprogress

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.ryuukossei.gymprogress.timer.RestTimer
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.navigation.MainNavigation
import br.com.ryuukossei.gymprogress.ui.setup.SetupScreen
import br.com.ryuukossei.gymprogress.ui.theme.GymProgressTheme

class MainActivity : ComponentActivity() {

    /** Sessão a abrir quando o app é aberto pela notificação do cronômetro. */
    private val sessionToOpen = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readSessionExtra(intent)
        setContent {
            GymProgressTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        sessionToOpen = sessionToOpen.value,
                        onSessionOpened = { sessionToOpen.value = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readSessionExtra(intent)
    }

    override fun onResume() {
        super.onResume()
        (application as GymProgressApp).container.restTimer.clearIfExpired()
    }

    private fun readSessionExtra(intent: Intent?) {
        val sessionId = intent?.getStringExtra(RestTimer.EXTRA_SESSION_ID) ?: return
        sessionToOpen.value = sessionId
        // Consome o pedido: se o app for reaberto depois (ex.: pelos recentes), não volta
        // a abrir essa sessão, que pode já ter sido finalizada ou cancelada.
        intent.removeExtra(RestTimer.EXTRA_SESSION_ID)
        setIntent(intent)
    }
}

/** Sem token configurado, mostra a configuração inicial; com token, as telas principais. */
@Composable
private fun AppRoot(sessionToOpen: String?, onSessionOpened: () -> Unit) {
    val settings by appContainer().settings.state.collectAsStateWithLifecycle()
    if (settings.hasToken) {
        MainNavigation(sessionToOpen = sessionToOpen, onSessionOpened = onSessionOpened)
    } else {
        SetupScreen()
    }
}
