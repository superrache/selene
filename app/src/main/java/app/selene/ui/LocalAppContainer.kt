package app.selene.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.selene.AppContainer

/** Lets tests provide their own container (in-memory database). Null → the application's. */
val LocalAppContainer = staticCompositionLocalOf<AppContainer?> { null }
