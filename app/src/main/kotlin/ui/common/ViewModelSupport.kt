package br.com.ryuukossei.gymprogress.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import br.com.ryuukossei.gymprogress.AppContainer
import br.com.ryuukossei.gymprogress.GymProgressApp

/** Acesso às dependências do app a partir das telas. */
@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as GymProgressApp).container
