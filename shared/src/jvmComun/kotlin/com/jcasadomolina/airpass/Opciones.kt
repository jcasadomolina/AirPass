package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.BaseDeDatos
import com.jcasadomolina.airpass.core.DesbloqueoRapido

/** Texto secundario dentro de una tarjeta de opción, con el sangrado de las filas. */
@Composable
fun NotaOpcion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
    )
}

/**
 * Pantalla de opciones de la base abierta. El desbloqueo rápido se activa con un interruptor que pide la contraseña
 * maestra y cuántos caracteres usar: AirPass la comprueba y prepara el código, porque la contraseña no se guarda.
 * `alCambiarRapido` recibe el desbloqueo nuevo o null para que la plataforma lo use y recuerde la preferencia.
 * `extras` añade opciones propias de la plataforma, como la huella en Android.
 */
@Composable
fun PantallaOpciones(
    base: BaseDeDatos,
    rapido: DesbloqueoRapido?,
    alCambiarRapido: (DesbloqueoRapido?) -> Unit,
    alVolver: () -> Unit,
    extras: @Composable ColumnScope.() -> Unit = {},
) {
    var activando by remember { mutableStateOf(false) }

    AtrasDelSistema(activo = true, alVolver = alVolver)

    Column(Modifier.fillMaxSize()) {
        BarraSuperior("Opciones", alVolver)
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = ANCHO_MAXIMO).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TarjetaDesplegable(
                    titulo = "Desbloqueo rápido",
                    desplegada = rapido != null,
                    alCambiar = { encender ->
                        if (encender) {
                            activando = true
                        } else {
                            rapido?.olvidar()
                            alCambiarRapido(null)
                        }
                    },
                    resumen = rapido?.let { "Últimos ${it.caracteres}" },
                    conInterruptor = true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NotaOpcion(
                        "Al bloquear, basta con los últimos caracteres de la contraseña. Un solo intento; caduca a los " +
                            "${DesbloqueoRapido.CADUCIDAD_MINUTOS} minutos. Para cambiar cuántos caracteres, desactívalo y vuelve a activarlo.",
                    )
                }
                extras()
            }
        }
    }

    if (activando) {
        DialogoActivarRapido(
            base = base,
            alActivar = {
                activando = false
                alCambiarRapido(it)
            },
            alCancelar = { activando = false },
        )
    }
}

/**
 * Pide la contraseña maestra y cuántos caracteres usar para activar el desbloqueo rápido. Solo lo activa si la
 * contraseña es la de la base y es más larga que el código; la contraseña se borra al terminar.
 */
@Composable
private fun DialogoActivarRapido(base: BaseDeDatos, alActivar: (DesbloqueoRapido) -> Unit, alCancelar: () -> Unit) {
    // shortcut: igual que la contraseña maestra, el campo de Compose guarda el texto en un String.
    var contrasena by remember { mutableStateOf("") }
    var caracteres by remember { mutableStateOf(3) }
    var error by remember { mutableStateOf<String?>(null) }

    fun activar() {
        val texto = contrasena.toCharArray()
        contrasena = ""
        try {
            val correcta = base.comprobarContrasena(texto)
            val rapido = if (correcta) DesbloqueoRapido.desde(texto, caracteres) else null
            when {
                rapido != null -> alActivar(rapido)
                correcta -> error = "La contraseña debe tener más de $caracteres caracteres."
                else -> error = "La contraseña no es correcta."
            }
        } finally {
            texto.fill('\u0000')
        }
    }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Activar el desbloqueo rápido") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("AirPass no guarda la contraseña maestra: escríbela para preparar el código.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Últimos", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    for (n in 3..6) FilterChip(selected = n == caracteres, onClick = { caracteres = n }, label = { Text("$n") })
                }
                OutlinedTextField(
                    value = contrasena,
                    onValueChange = {
                        contrasena = it
                        error = null
                    },
                    label = { Text("Contraseña maestra") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { activar() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { activar() }, enabled = contrasena.isNotEmpty()) { Text("Activar") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
