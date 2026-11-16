package com.jcasadomolina.airpass

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/** En Android, «atrás» lo gestiona el BackHandler más interno que esté activo. */
@Composable
actual fun AtrasDelSistema(activo: Boolean, alVolver: () -> Unit) = BackHandler(activo, alVolver)
