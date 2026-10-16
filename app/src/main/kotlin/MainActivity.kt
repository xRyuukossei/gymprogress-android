package br.com.ryuukossei.gymprogress

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.ryuukossei.gymprogress.ui.common.appContainer
import br.com.ryuukossei.gymprogress.ui.navigation.MainNavigation
import br.com.ryuukossei.gymprogress.ui.setup.SetupScreen
import br.com.ryuukossei.gymprogress.ui.theme.GymProgressTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymProgressTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

/** Sem token configurado, mostra a configuração inicial; com token, as telas principais. */
@Composable
private fun AppRoot() {
    val settings by appContainer().settings.state.collectAsStateWithLifecycle()
    if (settings.hasToken) MainNavigation() else SetupScreen()
}
