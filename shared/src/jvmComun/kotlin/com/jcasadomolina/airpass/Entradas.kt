package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
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
        Text(nombre, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Detalle de una entrada con todos sus datos. La contraseña sale oculta, siempre con los mismos puntos
 * para no revelar su longitud, y el menú ⋮ permite copiarla o mostrarla; ese estado se pierde al cerrar
 * el diálogo. `alBorrar` es opcional porque el escritorio borra desde la lista. Si la entrada tiene
 * historial, un botón con la fecha de la versión anterior permite volver a ella tras confirmarlo.
 */
@Composable
fun DialogoDetalle(
    entrada: Entrada,
    alCopiar: () -> Unit,
    alEditar: () -> Unit,
    alRestaurar: () -> Unit,
    alCerrar: () -> Unit,
    alBorrar: (() -> Unit)? = null,
) {
    var visible by remember(entrada) { mutableStateOf(false) }
    var confirmarRestaurar by remember(entrada) { mutableStateOf(false) }
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
                    Text("Contraseña", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (visible && secreto != null) secreto.toString(Charsets.UTF_8) else "••••••••••",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                        Box {
                            IconButton(onClick = { menu = true }, enabled = secreto != null) {
                                Icon(Iconos.Opciones, contentDescription = "Opciones de la contraseña")
                            }
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
                entrada.versionAnterior?.let { anterior ->
                    OutlinedButton(onClick = { confirmarRestaurar = true }) { Text("Volver a la versión del ${fecha(anterior)}") }
                }
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

    if (confirmarRestaurar) {
        AlertDialog(
            onDismissRequest = { confirmarRestaurar = false },
            title = { Text("Volver a la versión anterior") },
            text = {
                Text(
                    "La entrada recuperará los datos y la contraseña que tenía el ${fecha(entrada.versionAnterior)}. " +
                        "La versión actual se guarda en el historial, así que podrás deshacerlo. " +
                        "El cambio no llega al fichero hasta que guardes.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmarRestaurar = false
                    alRestaurar()
                }) { Text("Volver") }
            },
            dismissButton = { TextButton(onClick = { confirmarRestaurar = false }) { Text("Cancelar") } },
        )
    }
}
