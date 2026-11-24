package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.DesbloqueoRapido
import kotlinx.coroutines.delay

/** Casilla de la pantalla de apertura para permitir el desbloqueo rápido y elegir cuántos caracteres; null es desactivado. */
@Composable
fun OpcionDesbloqueoRapido(caracteres: Int?, alCambiar: (Int?) -> Unit) {
    Column(modifier = Modifier.widthIn(max = 400.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.toggleable(value = caracteres != null, role = Role.Checkbox) { alCambiar(if (it) 3 else null) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = caracteres != null, onCheckedChange = null)
            Text("Permitir desbloqueo rápido", style = MaterialTheme.typography.bodyMedium)
        }
        if (caracteres != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Con los últimos", style = MaterialTheme.typography.bodySmall)
                for (n in 3..6) FilterChip(selected = n == caracteres, onClick = { alCambiar(n) }, label = { Text("$n") })
            }
            Text(
                "Un solo intento. Caduca a los ${DesbloqueoRapido.CADUCIDAD_MINUTOS} minutos de bloquear.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Pantalla de la base bloqueada con desbloqueo rápido. La base sigue abierta en memoria, pero oculta. Pide los
 * últimos caracteres de la contraseña y da un único intento: si falla, o si pasa el tiempo de caducidad, llama a
 * `alCerrar` con el motivo para cerrar la base del todo. «Usar la contraseña completa» la cierra sin motivo.
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

    LaunchedEffect(rapido) {
        delay(rapido.restante() / 1_000_000)
        alCerrar("El desbloqueo rápido ha caducado. Escribe la contraseña completa.")
    }

    fun probar() {
        if (codigo.length != rapido.caracteres) return
        val intento = codigo.toCharArray()
        codigo = ""
        val acierto = rapido.intentar(intento)
        intento.fill('\u0000')
        if (acierto) {
            alDesbloquear()
        } else {
            alCerrar("Código incorrecto. Por seguridad la base se ha cerrado: escribe la contraseña completa.")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AirPass", style = MaterialTheme.typography.headlineMedium)
        Text("Base bloqueada", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = codigo,
            onValueChange = { codigo = it },
            label = { Text("Últimos ${rapido.caracteres} caracteres de la contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { probar() }),
            modifier = Modifier.fillMaxWidth().widthIn(max = 400.dp),
        )
        Text("Solo hay un intento.", style = MaterialTheme.typography.bodySmall)
        if (sinGuardar) {
            Text(
                "Hay cambios sin guardar: si fallas el código o caduca, se perderán.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = { probar() }, enabled = codigo.length == rapido.caracteres) { Text("Desbloquear") }
        TextButton(onClick = { alCerrar(null) }) { Text("Usar la contraseña completa") }
    }
}
