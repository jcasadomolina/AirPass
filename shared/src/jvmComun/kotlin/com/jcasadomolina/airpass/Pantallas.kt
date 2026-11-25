package com.jcasadomolina.airpass

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val ANCHO_MAXIMO = 440.dp

/**
 * Pantalla de apertura común. El candado se cierra al llegar y se abre cuando `estado` pasa a Hecho. Cada plataforma
 * pone cómo se elige el fichero (`alElegir`) y cómo se abre: `alAbrir` recibe la contraseña, que debe borrar.
 * `acciones` añade botones debajo de «Abrir», como «Abrir con huella» en Android.
 */
@Composable
fun PantallaApertura(
    fichero: String?,
    ruta: String?,
    estado: EstadoAccion,
    error: String?,
    alElegir: () -> Unit,
    alAbrir: (contrasena: CharArray) -> Unit,
    acciones: @Composable ColumnScope.() -> Unit = {},
) {
    // shortcut: el campo de texto de Compose guarda la contraseña en un String, que no se
    // puede borrar de la memoria; sustituirlo por un campo propio al endurecer SR-06.
    var contrasena by remember { mutableStateOf("") }
    var llegada by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { llegada = false }
    val ocupado = estado != EstadoAccion.Reposo

    fun abrir() {
        if (fichero == null || ocupado) return
        val caracteres = contrasena.toCharArray()
        contrasena = ""
        alAbrir(caracteres)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CandadoAnimado(abierto = llegada || estado == EstadoAccion.Hecho)
            Text("AirPass", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
                modifier = Modifier.widthIn(max = ANCHO_MAXIMO).fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                fichero ?: "Ningún fichero elegido",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            ruta?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        TextButton(onClick = alElegir, enabled = !ocupado) { Text(if (fichero == null) "Elegir…" else "Cambiar") }
                    }
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = { contrasena = it },
                        label = { Text("Contraseña maestra") },
                        singleLine = true,
                        enabled = !ocupado,
                        isError = error != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { abrir() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            BotonAccion("Abrir", estado, { abrir() }, habilitado = fichero != null, textoTrabajando = "Derivando la clave…")
            acciones()
            AnimatedVisibility(visible = error != null) {
                Text(
                    error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = ANCHO_MAXIMO),
                )
            }
        }
    }
}

/**
 * Cabecera de la base abierta: nombre, número de entradas y estado, el botón «Guardar», que se transforma mientras
 * guarda y al terminar, las opciones y el candado para bloquear. Debajo, el último aviso o error.
 */
@Composable
fun CabeceraBase(
    titulo: String,
    subtitulo: String,
    aviso: String?,
    error: String?,
    estadoGuardar: EstadoAccion,
    puedeGuardar: Boolean,
    alGuardar: () -> Unit,
    alOpciones: () -> Unit,
    alBloquear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BotonAccion("Guardar", estadoGuardar, alGuardar, habilitado = puedeGuardar, textoTrabajando = "Guardando…")
            IconButton(onClick = alOpciones, enabled = estadoGuardar != EstadoAccion.Trabajando) {
                Icon(Iconos.Ajustes, contentDescription = "Opciones")
            }
            IconButton(onClick = alBloquear, enabled = estadoGuardar != EstadoAccion.Trabajando) {
                Icon(Iconos.Candado, contentDescription = "Bloquear")
            }
        }
        AnimatedVisibility(visible = aviso != null) {
            Text(aviso.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(visible = error != null) {
            Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
