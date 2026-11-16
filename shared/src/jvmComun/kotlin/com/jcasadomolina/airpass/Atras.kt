package com.jcasadomolina.airpass

import androidx.compose.runtime.Composable

/** Mientras `activo` sea true, el gesto o botón «atrás» del sistema llama a `alVolver`. En el escritorio no hay tal botón. */
@Composable
expect fun AtrasDelSistema(activo: Boolean, alVolver: () -> Unit)
