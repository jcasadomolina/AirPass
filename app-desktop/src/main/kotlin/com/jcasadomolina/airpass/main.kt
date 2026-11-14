package com.jcasadomolina.airpass

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    // Cerrar la ventana no sale directamente: Escritorio decide, por si hay cambios sin guardar.
    var pideSalir by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = { pideSalir = true },
        title = "AirPass",
    ) {
        Escritorio(
            pideSalir = pideSalir,
            alSalir = ::exitApplication,
            alCancelarSalida = { pideSalir = false },
        )
    }
}
