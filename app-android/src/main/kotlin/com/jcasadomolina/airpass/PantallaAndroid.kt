package com.jcasadomolina.airpass

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.BaseDeDatos
import com.jcasadomolina.airpass.core.ClaveIncorrectaException
import com.jcasadomolina.airpass.core.Entrada
import com.jcasadomolina.airpass.core.FicheroManipuladoException
import com.jcasadomolina.airpass.core.Grupo
import com.jcasadomolina.airpass.core.abrirKdbx
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// En Android no hay rutas: el fichero se identifica por una Uri que da el selector del sistema.
private class SesionAndroid(val base: BaseDeDatos, val nombre: String)

@Composable
fun PantallaAndroid() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val contexto = LocalContext.current
            val ambito = rememberCoroutineScope()
            var sesion by remember { mutableStateOf<SesionAndroid?>(null) }
            var borradoPendiente by remember { mutableStateOf<Job?>(null) }

            // La cuenta atrás vive aquí y no en la lista: bloquear no la cancela, y así lo
            // copiado se borra igual aunque la base de datos ya esté cerrada.
            fun copiar(secreto: ByteArray) {
                copiarSecreto(contexto, secreto)
                borradoPendiente?.cancel()
                borradoPendiente = ambito.launch {
                    delay(SEGUNDOS_HASTA_BORRAR * 1000L)
                    borrarPortapapeles(contexto)
                }
            }

            Box(modifier = Modifier.safeDrawingPadding()) {
                val abierta = sesion
                if (abierta == null) {
                    PantallaAbrir(alAbrir = { sesion = it })
                } else {
                    PantallaEntradas(
                        sesion = abierta,
                        alCopiar = { copiar(it) },
                        alBloquear = {
                            abierta.base.borrarSecretos()
                            sesion = null
                        },
                    )
                }
            }
        }
    }
}

private fun nombreDe(contexto: Context, uri: Uri): String? =
    contexto.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }

@Composable
private fun PantallaAbrir(alAbrir: (SesionAndroid) -> Unit) {
    val contexto = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var nombre by remember { mutableStateOf<String?>(null) }
    // shortcut: el campo de texto de Compose guarda la contraseña en un String, que no se
    // puede borrar de la memoria; sustituirlo por un campo propio al endurecer SR-06.
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var abriendo by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    // El selector de documentos del sistema: AirPass solo recibe acceso al fichero elegido,
    // sin pedir permiso para leer todo el almacenamiento.
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { elegido ->
        if (elegido != null) {
            uri = elegido
            nombre = nombreDe(contexto, elegido)
            error = null
        }
    }

    fun abrir() {
        val elegido = uri ?: return
        if (abriendo) return
        val caracteres = contrasena.toCharArray()
        contrasena = ""
        error = null
        abriendo = true
        ambito.launch {
            try {
                // La derivación de la clave tarda a propósito: fuera del hilo de la interfaz.
                val base = withContext(Dispatchers.IO) {
                    val datos = contexto.contentResolver.openInputStream(elegido)?.use { it.readBytes() }
                        ?: throw IOException("No se puede leer el fichero")
                    abrirKdbx(datos, caracteres)
                }
                alAbrir(SesionAndroid(base, nombre ?: "Base de datos"))
            } catch (e: ClaveIncorrectaException) {
                error = "La contraseña no es correcta o el fichero ha sido manipulado."
            } catch (e: FicheroManipuladoException) {
                error = "El contenido del fichero ha sido modificado o está dañado."
            } catch (e: IllegalArgumentException) {
                error = "No se puede abrir el fichero. ${e.message}"
            } catch (e: IOException) {
                error = "No se puede leer el fichero."
            } finally {
                caracteres.fill('\u0000')
                abriendo = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AirPass", style = MaterialTheme.typography.headlineMedium)

        OutlinedButton(onClick = { elegir.launch(arrayOf("*/*")) }, enabled = !abriendo) {
            Text("Elegir base de datos…")
        }
        Text(nombre ?: "Ningún fichero elegido", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña maestra") },
            singleLine = true,
            enabled = !abriendo,
            isError = error != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { abrir() }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (abriendo) {
            CircularProgressIndicator()
            Text("Derivando la clave. Puede tardar un poco.", style = MaterialTheme.typography.bodySmall)
        } else {
            Button(onClick = { abrir() }, enabled = uri != null) { Text("Abrir") }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun entradasConGrupo(grupo: Grupo, ruta: String = grupo.nombre): List<Pair<String, Entrada>> =
    grupo.entradas.map { ruta to it } +
        grupo.grupos.flatMap { entradasConGrupo(it, "$ruta / ${it.nombre}") }

@Composable
private fun PantallaEntradas(sesion: SesionAndroid, alCopiar: (ByteArray) -> Unit, alBloquear: () -> Unit) {
    val entradas = remember(sesion) { entradasConGrupo(sesion.base.raiz) }
    var detalle by remember { mutableStateOf<Entrada?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(sesion.nombre, style = MaterialTheme.typography.titleMedium)
                Text("${entradas.size} entradas", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = alBloquear) { Text("Bloquear") }
        }
        HorizontalDivider()
        LazyColumn {
            items(entradas) { (grupo, entrada) ->
                ListItem(
                    modifier = Modifier.clickable { detalle = entrada },
                    overlineContent = { Text(grupo) },
                    headlineContent = { Text(entrada.campos["Title"].orEmpty().ifEmpty { "(sin título)" }) },
                    supportingContent = { Text(entrada.campos["UserName"].orEmpty()) },
                )
                HorizontalDivider()
            }
        }
    }

    detalle?.let { entrada ->
        DialogoDetalle(
            entrada = entrada,
            alCopiar = { entrada.protegidos["Password"]?.let(alCopiar) },
            alCerrar = { detalle = null },
        )
    }
}

private val formatoFecha: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, HH:mm", Locale.forLanguageTag("es"))
        .withZone(ZoneId.systemDefault())

private fun fecha(instante: Instant?): String = instante?.let { formatoFecha.format(it) } ?: "Desconocida"

@Composable
private fun Dato(nombre: String, valor: String) {
    Column {
        Text(nombre, style = MaterialTheme.typography.labelMedium)
        Text(valor.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

// Igual que en el escritorio: la contraseña vuelve a salir oculta al reabrir el detalle.
@Composable
private fun DialogoDetalle(entrada: Entrada, alCopiar: () -> Unit, alCerrar: () -> Unit) {
    var visible by remember(entrada) { mutableStateOf(false) }
    var menu by remember(entrada) { mutableStateOf(false) }
    var copiada by remember(entrada) { mutableStateOf(false) }
    val secreto = entrada.protegidos["Password"]

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(entrada.campos["Title"].orEmpty().ifEmpty { "(sin título)" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Dato("Usuario", entrada.campos["UserName"].orEmpty())
                Column {
                    Text("Contraseña", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            // shortcut: mostrarla exige un String, que no se puede borrar de la memoria.
                            // Oculta, siempre el mismo número de puntos: no revela su longitud.
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
                Dato("Creada", fecha(entrada.creada))
                Dato("Modificada", fecha(entrada.modificada))
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text("Cerrar") } },
    )
}
