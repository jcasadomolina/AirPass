package com.jcasadomolina.airpass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.Caracteres
import com.jcasadomolina.airpass.core.OpcionesGenerador
import com.jcasadomolina.airpass.core.PARECIDOS
import com.jcasadomolina.airpass.core.bits
import com.jcasadomolina.airpass.core.generarContrasena
import com.jcasadomolina.airpass.core.problema

private val LONGITUDES = listOf(6, 8, 12, 16, 24, 32)

/**
 * Generador de contraseñas para ponerlo bajo el campo de contraseña. El medidor semicircular muestra la fuerza que
 * tendrá una contraseña con estas opciones; la longitud se elige entre las habituales o se escribe, y los tipos de
 * caracteres se marcan con casillas redondas. «Generar contraseña» crea una y la entrega con `alGenerar`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PanelGenerador(alGenerar: (String) -> Unit) {
    var opciones by remember { mutableStateOf(OpcionesGenerador()) }
    var longitudLibre by remember { mutableStateOf("") }
    val problema = opciones.problema()

    fun generar() {
        if (opciones.problema() != null) return
        val generada = generarContrasena(opciones)
        // shortcut: el campo de contraseña trabaja con un String, que no se puede borrar de la memoria.
        alGenerar(String(generada))
        generada.fill('\u0000')
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MedidorFuerza(opciones.bits())

        Text("Longitud", style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
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
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Caracteres", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            AnimatedContent(
                targetState = "${opciones.caracteres.size} de ${Caracteres.entries.size}",
                transitionSpec = { slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut() },
            ) {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth()) {
            for (grupo in Caracteres.entries) {
                FilaCasilla(grupo.nombre, grupo in opciones.caracteres) { marcada ->
                    opciones = opciones.copy(caracteres = if (marcada) opciones.caracteres + grupo else opciones.caracteres - grupo)
                }
            }
            FilaCasilla("Excluir caracteres parecidos ($PARECIDOS)", opciones.excluirParecidos) {
                opciones = opciones.copy(excluirParecidos = it)
            }
            FilaCasilla("Al menos uno de cada grupo", opciones.unoDeCada) { opciones = opciones.copy(unoDeCada = it) }
        }
        problema?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        BotonAccion("Generar contraseña", EstadoAccion.Reposo, { generar() }, habilitado = problema == null)
    }
}
