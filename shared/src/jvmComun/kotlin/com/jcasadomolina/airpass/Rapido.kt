package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.DesbloqueoRapido
import kotlinx.coroutines.delay

/**
 * Pantalla de la base bloqueada con desbloqueo rápido. La base sigue abierta en memoria, pero oculta. Pide los
 * últimos caracteres de la contraseña y da un único intento: si acierta, el candado se abre y el botón termina en ✓;
 * si falla, o si pasa el tiempo de caducidad, llama a `alCerrar` con el motivo para cerrar la base del todo.
 * «Usar la contraseña completa» la cierra sin motivo.
 */
@Composable
fun PantallaDesbloqueoRapido(
    rapido: DesbloqueoRapido,
    sinGuardar: Boolean,
    alDesbloquear: () -> Unit,
    alCerrar: (motivo: String?) -> Unit,
) {
    // shortcut: igual que la contraseña maestra, el campo de Compose guarda el texto en un String.
    var codigo by remember { mutableStateOf("") }
    var estado by remember { mutableStateOf(EstadoAccion.Reposo) }

    LaunchedEffect(rapido) {
        delay(rapido.restante() / 1_000_000)
        alCerrar("El desbloqueo rápido ha caducado. Escribe la contraseña completa.")
    }
    LaunchedEffect(estado) {
        if (estado == EstadoAccion.Hecho) {
            delay(650)
            alDesbloquear()
        }
    }

    fun probar() {
        if (codigo.length != rapido.caracteres || estado != EstadoAccion.Reposo) return
        val intento = codigo.toCharArray()
        codigo = ""
        val acierto = rapido.intentar(intento)
        intento.fill('\u0000')
        if (acierto) {
            estado = EstadoAccion.Hecho
        } else {
            alCerrar("Código incorrecto. Por seguridad la base se ha cerrado: escribe la contraseña completa.")
        }
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CandadoAnimado(abierto = estado == EstadoAccion.Hecho)
            Text("Base bloqueada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.widthIn(max = ANCHO_MAXIMO).fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = codigo,
                        onValueChange = { codigo = it },
                        label = { Text("Últimos ${rapido.caracteres} caracteres") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { probar() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Solo hay un intento.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sinGuardar) {
                        Text(
                            "Hay cambios sin guardar: si fallas el código o caduca, se perderán.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            BotonAccion("Desbloquear", estado, { probar() }, habilitado = codigo.length == rapido.caracteres)
            TextButton(onClick = { alCerrar(null) }, enabled = estado == EstadoAccion.Reposo) {
                Text("Usar la contraseña completa", textAlign = TextAlign.Center)
            }
        }
    }
}
