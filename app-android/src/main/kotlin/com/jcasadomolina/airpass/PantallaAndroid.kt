package com.jcasadomolina.airpass

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.jcasadomolina.airpass.core.anadirEntrada
import com.jcasadomolina.airpass.core.borrarEntrada
import com.jcasadomolina.airpass.core.guardarVerificado
import com.jcasadomolina.airpass.core.modificarEntrada
import com.jcasadomolina.airpass.core.restaurarVersionAnterior
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// En Android no hay rutas: el fichero se identifica por una Uri que da el selector del sistema.
private class SesionAndroid(val base: BaseDeDatos, val uri: Uri, val nombre: String)

@Composable
fun PantallaAndroid() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val contexto = LocalContext.current
            val ambito = rememberCoroutineScope()
            var sesion by remember { mutableStateOf<SesionAndroid?>(null) }
            var borradoPendiente by remember { mutableStateOf<Job?>(null) }
            var sinGuardar by remember { mutableStateOf(false) }
            // Acción que espera a que el usuario confirme que descarta sus cambios.
            var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }

            fun bloquear() {
                sesion?.base?.borrarSecretos()
                sesion = null
                sinGuardar = false
            }

            fun intentar(accion: () -> Unit) {
                if (sinGuardar) pendiente = accion else accion()
            }

            // Con una base abierta, «atrás» bloquea en lugar de cerrar la app sin avisar.
            BackHandler(enabled = sesion != null) { intentar { bloquear() } }

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
                        sinGuardar = sinGuardar,
                        alCambiar = { sinGuardar = true },
                        alGuardar = { sinGuardar = false },
                        alCopiar = { copiar(it) },
                        alBloquear = { intentar { bloquear() } },
                    )
                }
            }

            pendiente?.let { accion ->
                AlertDialog(
                    onDismissRequest = { pendiente = null },
                    title = { Text("Hay cambios sin guardar") },
                    text = { Text("Si continúas, los cambios hechos desde el último guardado se perderán.") },
                    confirmButton = {
                        TextButton(onClick = { pendiente = null; accion() }) { Text("Descartar cambios") }
                    },
                    dismissButton = { TextButton(onClick = { pendiente = null }) { Text("Cancelar") } },
                )
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
    var uri by remember { mutableStateOf(leerUltimaBase(contexto)) }
    var nombre by remember { mutableStateOf(uri?.let { nombreDe(contexto, it) }) }
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
                recordarUltimaBase(contexto, elegido)
                alAbrir(SesionAndroid(base, elegido, nombre ?: "Base de datos"))
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
            Text(if (uri == null) "Elegir base de datos…" else "Elegir otra base de datos…")
        }
        Text(nombre ?: "Ningún fichero elegido", style = MaterialTheme.typography.bodyMedium)
        uri?.let(::rutaLegible)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

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

// Qué está editando el diálogo: una entrada existente, o una nueva dentro de `grupo`.
private class Edicion(val entrada: Entrada?, val grupo: Grupo? = null)

@Composable
private fun PantallaEntradas(
    sesion: SesionAndroid,
    sinGuardar: Boolean,
    alCambiar: () -> Unit,
    alGuardar: () -> Unit,
    alCopiar: (ByteArray) -> Unit,
    alBloquear: () -> Unit,
) {
    val contexto = LocalContext.current
    val base = sesion.base
    // Cada cambio en la base de datos suma uno, y eso hace que la lista se vuelva a leer.
    var version by remember { mutableIntStateOf(0) }
    val raiz = remember(sesion, version) { base.raiz }
    var detalle by remember { mutableStateOf<Entrada?>(null) }
    var edicion by remember { mutableStateOf<Edicion?>(null) }
    var paraBorrar by remember { mutableStateOf<Entrada?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val ambito = rememberCoroutineScope()

    fun cambiado() {
        version++
        aviso = null
        error = null
        alCambiar()
    }

    fun guardar() {
        if (guardando) return
        guardando = true
        error = null
        ambito.launch {
            try {
                // Derivar la clave y verificar tarda: fuera del hilo de la interfaz.
                withContext(Dispatchers.IO) {
                    escribirEnUri(contexto.contentResolver, sesion.uri, guardarVerificado(base))
                }
                alGuardar()
                aviso = "Cambios guardados."
            } catch (e: Exception) {
                error = "No se han podido guardar los cambios. ${e.message.orEmpty()}"
            } finally {
                guardando = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(sesion.nombre, style = MaterialTheme.typography.titleMedium)
            Text(
                "${raiz.totalEntradas()} entradas" + if (sinGuardar) " · cambios sin guardar" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // Mientras se guarda no se edita: el documento se está leyendo en otro hilo.
                Button(onClick = { guardar() }, enabled = sinGuardar && !guardando) { Text("Guardar") }
                OutlinedButton(onClick = alBloquear, enabled = !guardando) { Text("Bloquear") }
                if (guardando) CircularProgressIndicator()
            }
        }
        HorizontalDivider()
        NavegadorGrupos(
            base = base,
            raiz = raiz,
            habilitado = !guardando,
            alCambiar = { cambiado() },
            alAbrirEntrada = { detalle = it },
            alNuevaEntrada = { edicion = Edicion(null, it) },
            modifier = Modifier.fillMaxSize(),
        )
    }

    detalle?.let { entrada ->
        DialogoDetalle(
            entrada = entrada,
            alCopiar = { entrada.protegidos["Password"]?.let(alCopiar) },
            alEditar = {
                detalle = null
                edicion = Edicion(entrada)
            },
            alBorrar = {
                detalle = null
                paraBorrar = entrada
            },
            alRestaurar = {
                base.restaurarVersionAnterior(entrada)
                detalle = null
                cambiado()
            },
            alCerrar = { detalle = null },
        )
    }

    edicion?.let { actual ->
        DialogoEntrada(
            entrada = actual.entrada,
            alAceptar = { datos, contrasena ->
                val existente = actual.entrada
                if (existente == null) {
                    base.anadirEntrada(actual.grupo ?: base.raiz, datos, contrasena)
                } else {
                    base.modificarEntrada(existente, datos, contrasena)
                }
                contrasena.fill('\u0000')
                edicion = null
                cambiado()
            },
            alCancelar = { edicion = null },
        )
    }

    paraBorrar?.let { entrada ->
        AlertDialog(
            onDismissRequest = { paraBorrar = null },
            title = { Text("Borrar la entrada") },
            text = {
                Text(
                    "«${entrada.campos["Title"].orEmpty()}» se moverá a la papelera, o se eliminará si la " +
                        "base de datos no tiene papelera. El cambio no llega al fichero hasta que guardes.",
                )
            },
            confirmButton = {
                TextButton(onClick = { base.borrarEntrada(entrada); paraBorrar = null; cambiado() }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { paraBorrar = null }) { Text("Cancelar") } },
        )
    }
}
