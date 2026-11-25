package com.jcasadomolina.airpass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.DatosEntrada
import com.jcasadomolina.airpass.core.Entrada
import com.jcasadomolina.airpass.core.estimarBits
import com.jcasadomolina.airpass.core.texto

/**
 * Pantalla que entra deslizándose desde la derecha y tapa la lista sin quitarla, así que al volver la lista sigue en
 * el mismo grupo. Se usa para editar una entrada y para las opciones.
 */
@Composable
fun PantallaSuperpuesta(visible: Boolean, contenido: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it / 3 } + fadeIn(),
        exit = slideOutHorizontally { it / 3 } + fadeOut(),
    ) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) { contenido() }
    }
}

/**
 * Pantalla para crear (`entrada` null) o editar una entrada, en secciones: datos, contraseña, notas y etiquetas.
 * La contraseña se ve en claro, con una barra de fuerza debajo y el generador plegado bajo ella. Las etiquetas son
 * chips que se quitan con su ✕. Al aceptar entrega los datos y la contraseña como CharArray, que se debe borrar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaEdicion(entrada: Entrada?, alAceptar: (DatosEntrada, CharArray) -> Unit, alCancelar: () -> Unit) {
    var titulo by remember { mutableStateOf(entrada?.texto("Title").orEmpty()) }
    var usuario by remember { mutableStateOf(entrada?.texto("UserName").orEmpty()) }
    var url by remember { mutableStateOf(entrada?.texto("URL").orEmpty()) }
    // shortcut: la contraseña se edita visible, así que vive en un String que no se puede borrar de la memoria.
    var contrasena by remember { mutableStateOf(entrada?.protegidos?.get("Password")?.toString(Charsets.UTF_8).orEmpty()) }
    var notas by remember { mutableStateOf(entrada?.texto("Notes").orEmpty()) }
    var etiquetas by remember { mutableStateOf(entrada?.etiquetas.orEmpty()) }
    var nuevaEtiqueta by remember { mutableStateOf("") }
    var generador by remember { mutableStateOf(false) }
    var faltaContrasena by remember { mutableStateOf(false) }

    fun anadirEtiqueta() {
        val limpia = nuevaEtiqueta.trim().trim(',', ';').trim()
        if (limpia.isNotEmpty() && limpia !in etiquetas) etiquetas = etiquetas + limpia
        nuevaEtiqueta = ""
    }

    fun aceptar() {
        if (titulo.isBlank()) return
        if (contrasena.isEmpty()) {
            faltaContrasena = true
            return
        }
        anadirEtiqueta()
        alAceptar(DatosEntrada(titulo, usuario, url, notas, etiquetas), contrasena.toCharArray())
    }

    AtrasDelSistema(activo = true, alVolver = alCancelar)

    Column(Modifier.fillMaxSize()) {
        BarraSuperior(if (entrada == null) "Nueva entrada" else "Editar entrada", alCancelar) {
            BotonAccion("Aceptar", EstadoAccion.Reposo, { aceptar() }, habilitado = titulo.isNotBlank())
        }
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(max = ANCHO_MAXIMO).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Seccion("Entrada") {
                    OutlinedTextField(titulo, { titulo = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(usuario, { usuario = it }, label = { Text("Usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        url,
                        { url = it },
                        label = { Text("URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Seccion("Contraseña") {
                    OutlinedTextField(
                        value = contrasena,
                        onValueChange = {
                            contrasena = it
                            faltaContrasena = false
                        },
                        label = { Text("Contraseña") },
                        singleLine = true,
                        isError = faltaContrasena,
                        supportingText = if (faltaContrasena) {
                            { Text("Falta la contraseña. Escríbela o genera una.") }
                        } else {
                            null
                        },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BarraFuerza(estimarBits(contrasena))
                    TarjetaDesplegable(
                        titulo = "Generador",
                        desplegada = generador,
                        alCambiar = { generador = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        PanelGenerador {
                            contrasena = it
                            faltaContrasena = false
                        }
                    }
                }
                Seccion("Notas") {
                    OutlinedTextField(notas, { notas = it }, placeholder = { Text("Sin notas") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                }
                Seccion("Etiquetas") {
                    if (etiquetas.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (etiqueta in etiquetas) {
                                InputChip(
                                    selected = false,
                                    onClick = { etiquetas = etiquetas - etiqueta },
                                    label = { Text(etiqueta) },
                                    trailingIcon = { Icon(Iconos.Cerrar, contentDescription = "Quitar $etiqueta", modifier = Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = nuevaEtiqueta,
                        onValueChange = {
                            nuevaEtiqueta = it
                            if (it.endsWith(',')) anadirEtiqueta()
                        },
                        label = { Text("Añadir etiqueta") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { anadirEtiqueta() }),
                        trailingIcon = {
                            IconButton(onClick = { anadirEtiqueta() }, enabled = nuevaEtiqueta.isNotBlank()) {
                                Icon(Iconos.Mas, contentDescription = "Añadir la etiqueta")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
