package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.Caracteres
import com.jcasadomolina.airpass.core.OpcionesGenerador
import com.jcasadomolina.airpass.core.PARECIDOS
import com.jcasadomolina.airpass.core.bits
import com.jcasadomolina.airpass.core.generarContrasena
import com.jcasadomolina.airpass.core.problema
import kotlin.math.roundToInt

private val LONGITUDES = listOf(6, 8, 12, 16, 24, 32)
private const val BITS_PARA_VERDE = 128.0
private val ROJO = Color(0xFFE53935)
private val AMARILLO = Color(0xFFFDD835)
private val VERDE = Color(0xFF43A047)

/** Color de la barra de fuerza: de rojo a amarillo en la primera mitad y de amarillo a verde en la segunda. */
private fun colorDeFuerza(fraccion: Float): Color =
    if (fraccion < 0.5f) lerp(ROJO, AMARILLO, fraccion * 2) else lerp(AMARILLO, VERDE, (fraccion - 0.5f) * 2)

/** Una opción con su casilla; se marca o desmarca pulsando en cualquier parte de la fila. */
@Composable
private fun Opcion(texto: String, marcada: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = marcada, role = Role.Checkbox, onValueChange = alCambiar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = marcada, onCheckedChange = null)
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Pantalla del generador. Muestra la contraseña, la regenera con ↻ y cada vez que cambian las opciones, y
 * enseña su fuerza en bits con una barra de rojo a verde (verde a partir de 128 bits). La longitud se elige
 * entre las habituales o se escribe. «Guardar» entrega la contraseña a quien abrió el generador.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogoGenerador(alGuardar: (String) -> Unit, alCancelar: () -> Unit) {
    var opciones by remember { mutableStateOf(OpcionesGenerador()) }
    var longitudLibre by remember { mutableStateOf("") }
    // shortcut: mostrarla exige un String, que no se puede borrar de la memoria; igual que en el detalle.
    var contrasena by remember { mutableStateOf("") }
    val problema = opciones.problema()
    val bits = opciones.bits()
    val fraccion = (bits / BITS_PARA_VERDE).coerceIn(0.0, 1.0).toFloat()

    fun generar() {
        if (opciones.problema() != null) {
            contrasena = ""
            return
        }
        val generada = generarContrasena(opciones)
        contrasena = String(generada)
        generada.fill('\u0000')
    }

    LaunchedEffect(opciones) { generar() }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Generador de contraseñas") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        contrasena.ifEmpty { "—" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { generar() }, enabled = problema == null) { Text("↻") }
                }
                LinearProgressIndicator(progress = { fraccion }, color = colorDeFuerza(fraccion), modifier = Modifier.fillMaxWidth())
                Text("${bits.roundToInt()} bits", style = MaterialTheme.typography.bodySmall)

                Text("Longitud", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (longitud in LONGITUDES) {
                        FilterChip(
                            selected = opciones.longitud == longitud,
                            onClick = {
                                longitudLibre = ""
                                opciones = opciones.copy(longitud = longitud)
                            },
                            label = { Text("$longitud") },
                        )
                    }
                }
                OutlinedTextField(
                    value = longitudLibre,
                    onValueChange = { texto ->
                        longitudLibre = texto.filter { it.isDigit() }.take(3)
                        longitudLibre.toIntOrNull()?.let { opciones = opciones.copy(longitud = it) }
                    },
                    label = { Text("Otra longitud") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                Text("Caracteres", style = MaterialTheme.typography.labelMedium)
                for (grupo in Caracteres.entries) {
                    Opcion(grupo.nombre, grupo in opciones.caracteres) { marcada ->
                        opciones = opciones.copy(caracteres = if (marcada) opciones.caracteres + grupo else opciones.caracteres - grupo)
                    }
                }
                Opcion("Excluir caracteres parecidos ($PARECIDOS)", opciones.excluirParecidos) {
                    opciones = opciones.copy(excluirParecidos = it)
                }
                Opcion("Al menos uno de cada grupo", opciones.unoDeCada) { opciones = opciones.copy(unoDeCada = it) }
                problema?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { alGuardar(contrasena) }, enabled = problema == null && contrasena.isNotEmpty()) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
