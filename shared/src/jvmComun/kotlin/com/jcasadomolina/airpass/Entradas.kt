package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.DatosEntrada
import com.jcasadomolina.airpass.core.Entrada
import com.jcasadomolina.airpass.core.texto
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val SEGUNDOS_HASTA_BORRAR = 12

private val formatoFecha: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es"))
        .withZone(ZoneId.systemDefault())

/** Fecha y hora en español y en la zona del equipo, o «Desconocida» si el fichero no la tiene. */
private fun fecha(instante: Instant?): String = instante?.let { formatoFecha.format(it) } ?: "Desconocida"

/** Un dato del detalle: su nombre pequeño encima y el valor debajo, con una raya si está vacío. */
@Composable
private fun Dato(nombre: String, valor: String) {
    Column {
        Text(nombre, style = MaterialTheme.typography.labelMedium)
        Text(valor.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Detalle de una entrada con todos sus datos. La contraseña sale oculta, siempre con los mismos puntos
 * para no revelar su longitud, y el menú ⋮ permite copiarla o mostrarla; ese estado se pierde al cerrar
 * el diálogo. `alBorrar` es opcional porque el escritorio borra desde la lista.
 */
@Composable
fun DialogoDetalle(
    entrada: Entrada,
    alCopiar: () -> Unit,
    alEditar: () -> Unit,
    alCerrar: () -> Unit,
    alBorrar: (() -> Unit)? = null,
) {
    var visible by remember(entrada) { mutableStateOf(false) }
    var menu by remember(entrada) { mutableStateOf(false) }
    var copiada by remember(entrada) { mutableStateOf(false) }
    val secreto = entrada.protegidos["Password"]

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(entrada.texto("Title").ifEmpty { "(sin título)" }) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Dato("Usuario", entrada.texto("UserName"))
                Column {
                    Text("Contraseña", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (visible && secreto != null) secreto.toString(Charsets.UTF_8) else "••••••••••",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            TextButton(onClick = { menu = true }, enabled = secreto != null) { Text("⋮") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Copiar contraseña") },
                                    onClick = {
                                        menu = false
                                        copiada = true
                                        alCopiar()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (visible) "Ocultar contraseña" else "Mostrar contraseña") },
                                    onClick = {
                                        menu = false
                                        visible = !visible
                                    },
                                )
                            }
                        }
                    }
                    if (copiada) {
                        Text(
                            "Copiada. Se borrará del portapapeles en $SEGUNDOS_HASTA_BORRAR segundos.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Dato("URL", entrada.texto("URL"))
                Dato("Notas", entrada.texto("Notes"))
                Dato("Etiquetas", entrada.etiquetas.joinToString(", "))
                Dato("Contraseña cambiada", fecha(entrada.contrasenaCambiada))
                Dato("Creada", fecha(entrada.creada))
                Dato("Modificada", fecha(entrada.modificada))
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
        dismissButton = {
            Row {
                if (alBorrar != null) TextButton(onClick = alBorrar) { Text("Borrar") }
                TextButton(onClick = alEditar) { Text("Editar") }
            }
        },
    )
}

/**
 * Diálogo para crear (`entrada` null) o editar una entrada. La contraseña es obligatoria: si falta se
 * avisa, y la llave 🔑 junto al campo abre el generador. La actual no se carga en el campo, para no mostrarla sin necesidad.
 * Las etiquetas se escriben separadas por comas. Al aceptar entrega la contraseña como CharArray, que
 * quien la recibe debe borrar.
 */
@Composable
fun DialogoEntrada(
    entrada: Entrada?,
    alAceptar: (DatosEntrada, CharArray) -> Unit,
    alCancelar: () -> Unit,
) {
    var titulo by remember { mutableStateOf(entrada?.texto("Title").orEmpty()) }
    var usuario by remember { mutableStateOf(entrada?.texto("UserName").orEmpty()) }
    var contrasena by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(entrada?.texto("URL").orEmpty()) }
    var notas by remember { mutableStateOf(entrada?.texto("Notes").orEmpty()) }
    var etiquetas by remember { mutableStateOf(entrada?.etiquetas.orEmpty().joinToString(", ")) }
    var faltaContrasena by remember { mutableStateOf(false) }
    var generando by remember { mutableStateOf(false) }

    fun aceptar() {
        if (contrasena.isEmpty()) {
            faltaContrasena = true
            return
        }
        val caracteres = contrasena.toCharArray()
        contrasena = ""
        alAceptar(DatosEntrada(titulo, usuario, url, notas, listOf(etiquetas)), caracteres)
    }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text(if (entrada == null) "Añadir entrada" else "Editar entrada") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(titulo, { titulo = it }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true)
                OutlinedTextField(
                    value = contrasena,
                    onValueChange = {
                        contrasena = it
                        faltaContrasena = false
                    },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    isError = faltaContrasena,
                    supportingText = {
                        if (faltaContrasena) Text("Falta la contraseña. Escríbela o genera una con la llave.")
                    },
                    trailingIcon = { TextButton(onClick = { generando = true }) { Text("🔑") } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(
                    url,
                    { url = it },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(notas, { notas = it }, label = { Text("Notas") }, minLines = 3)
                OutlinedTextField(
                    etiquetas,
                    { etiquetas = it },
                    label = { Text("Etiquetas") },
                    supportingText = { Text("Separadas por comas") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { aceptar() }, enabled = titulo.isNotBlank()) { Text("Aceptar") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )

    if (generando) {
        DialogoGenerador(
            alGuardar = {
                contrasena = it
                faltaContrasena = false
                generando = false
            },
            alCancelar = { generando = false },
        )
    }
}
