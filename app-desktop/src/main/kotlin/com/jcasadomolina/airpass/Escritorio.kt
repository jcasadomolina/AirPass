package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.jcasadomolina.airpass.core.guardarEnFichero
import com.jcasadomolina.airpass.core.modificarEntrada
import com.jcasadomolina.airpass.core.restaurarVersionAnterior
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class Sesion(val base: BaseDeDatos, val fichero: File)

// `pideSalir` llega a true cuando el usuario cierra la ventana. La aplicación no sale
// hasta que se llama a `alSalir`, para poder avisar si hay cambios sin guardar.
@Composable
fun Escritorio(pideSalir: Boolean, alSalir: () -> Unit, alCancelarSalida: () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var sesion by remember { mutableStateOf<Sesion?>(null) }
            var sinGuardar by remember { mutableStateOf(false) }
            // Acción que espera a que el usuario confirme que descarta sus cambios.
            var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }

            fun cerrarSesion() {
                Portapapeles.borrar()
                sesion?.base?.borrarSecretos()
                sesion = null
                sinGuardar = false
            }

            fun intentar(accion: () -> Unit) {
                if (sinGuardar) pendiente = accion else accion()
            }

            LaunchedEffect(pideSalir) {
                if (pideSalir) intentar { cerrarSesion(); alSalir() }
            }

            val abierta = sesion
            if (abierta == null) {
                PantallaAbrir(alAbrir = { base, fichero -> sesion = Sesion(base, fichero) })
            } else {
                PantallaEntradas(
                    sesion = abierta,
                    sinGuardar = sinGuardar,
                    alCambiar = { sinGuardar = true },
                    alGuardar = { sinGuardar = false },
                    alBloquear = { intentar { cerrarSesion() } },
                )
            }

            pendiente?.let { accion ->
                AlertDialog(
                    onDismissRequest = { pendiente = null; alCancelarSalida() },
                    title = { Text("Hay cambios sin guardar") },
                    text = { Text("Si continúas, los cambios hechos desde el último guardado se perderán.") },
                    confirmButton = {
                        TextButton(onClick = { pendiente = null; accion() }) { Text("Descartar cambios") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendiente = null; alCancelarSalida() }) { Text("Cancelar") }
                    },
                )
            }
        }
    }
}

private fun elegirFichero(): File? {
    val dialogo = FileDialog(null as Frame?, "Elegir base de datos", FileDialog.LOAD)
    dialogo.file = "*.kdbx"
    dialogo.isVisible = true
    return dialogo.file?.let { File(dialogo.directory, it) }
}

@Composable
private fun PantallaAbrir(alAbrir: (BaseDeDatos, File) -> Unit) {
    var fichero by remember { mutableStateOf(UltimaBase.leer()) }
    // shortcut: el campo de texto de Compose guarda la contraseña en un String, que no se
    // puede borrar de la memoria; sustituirlo por un campo propio al endurecer SR-06.
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var abriendo by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    fun abrir() {
        val elegido = fichero ?: return
        if (abriendo) return
        val caracteres = contrasena.toCharArray()
        contrasena = ""
        error = null
        abriendo = true
        ambito.launch {
            try {
                // La derivación de la clave tarda a propósito: fuera del hilo de la interfaz.
                val base = withContext(Dispatchers.IO) { abrirKdbx(elegido.readBytes(), caracteres) }
                UltimaBase.recordar(elegido)
                alAbrir(base, elegido)
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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AirPass", style = MaterialTheme.typography.headlineMedium)

        OutlinedButton(onClick = { elegirFichero()?.let { fichero = it; error = null } }) {
            Text(if (fichero == null) "Elegir base de datos…" else "Elegir otra base de datos…")
        }
        Text(fichero?.absolutePath ?: "Ningún fichero elegido", style = MaterialTheme.typography.bodyMedium)

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
            modifier = Modifier.width(320.dp),
        )

        if (abriendo) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { abrir() }, enabled = fichero != null) { Text("Abrir") }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

// Qué está editando el diálogo: una entrada existente, o una nueva dentro de `grupo`.
private class Edicion(val entrada: Entrada?, val grupo: Grupo? = null)

@Composable
private fun PantallaEntradas(
    sesion: Sesion,
    sinGuardar: Boolean,
    alCambiar: () -> Unit,
    alGuardar: () -> Unit,
    alBloquear: () -> Unit,
) {
    val base = sesion.base
    // Cada cambio en la base de datos suma uno, y eso hace que la lista se vuelva a leer.
    var version by remember { mutableIntStateOf(0) }
    val raiz = remember(version) { base.raiz }

    var aviso by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var borradoPendiente by remember { mutableStateOf<Job?>(null) }
    var edicion by remember { mutableStateOf<Edicion?>(null) }
    var paraBorrar by remember { mutableStateOf<Entrada?>(null) }
    var detalle by remember { mutableStateOf<Entrada?>(null) }
    val ambito = rememberCoroutineScope()

    fun cambiado() {
        version++
        error = null
        alCambiar()
    }

    fun copiarContrasena(entrada: Entrada) {
        val contrasena = entrada.protegidos["Password"] ?: return
        Portapapeles.copiarSecreto(contrasena)
        aviso = "Contraseña copiada. Se borrará del portapapeles en $SEGUNDOS_HASTA_BORRAR segundos."
        // Una copia nueva reinicia la cuenta atrás.
        borradoPendiente?.cancel()
        borradoPendiente = ambito.launch {
            delay(SEGUNDOS_HASTA_BORRAR * 1000L)
            Portapapeles.borrar()
            aviso = null
        }
    }

    fun guardar() {
        if (guardando) return
        guardando = true
        error = null
        ambito.launch {
            try {
                // Guardar vuelve a derivar la clave, y eso tarda: fuera del hilo de la interfaz.
                withContext(Dispatchers.IO) { guardarEnFichero(base, sesion.fichero.toPath()) }
                alGuardar()
                aviso = "Cambios guardados en ${sesion.fichero.name}."
            } catch (e: Exception) {
                error = "No se han podido guardar los cambios. El fichero no se ha modificado. ${e.message.orEmpty()}"
            } finally {
                guardando = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${raiz.totalEntradas()} entradas" + if (sinGuardar) " · cambios sin guardar" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (guardando) CircularProgressIndicator()
                // Mientras se guarda no se edita: el documento se está leyendo en otro hilo.
                Button(onClick = { guardar() }, enabled = sinGuardar && !guardando) { Text("Guardar") }
                OutlinedButton(onClick = alBloquear, enabled = !guardando) { Text("Bloquear") }
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
            accionesEntrada = { entrada ->
                Row {
                    if ("Password" in entrada.protegidos) {
                        TextButton(onClick = { copiarContrasena(entrada) }) { Text("Copiar contraseña") }
                    }
                    TextButton(onClick = { edicion = Edicion(entrada) }, enabled = !guardando) { Text("Editar") }
                    TextButton(onClick = { paraBorrar = entrada }, enabled = !guardando) { Text("Borrar") }
                }
            },
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
    detalle?.let { entrada ->
        DialogoDetalle(
            entrada = entrada,
            alCopiar = { copiarContrasena(entrada) },
            alEditar = {
                detalle = null
                edicion = Edicion(entrada)
            },
            alRestaurar = {
                base.restaurarVersionAnterior(entrada)
                detalle = null
                cambiado()
            },
            alCerrar = { detalle = null },
        )
    }
}
