package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jcasadomolina.airpass.core.BaseDeDatos
import com.jcasadomolina.airpass.core.ClaveIncorrectaException
import com.jcasadomolina.airpass.core.DesbloqueoRapido
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

private class Sesion(val base: BaseDeDatos, val fichero: File, var rapido: DesbloqueoRapido?)

// `pideSalir` llega a true cuando el usuario cierra la ventana. La aplicación no sale
// hasta que se llama a `alSalir`, para poder avisar si hay cambios sin guardar.
@Composable
fun Escritorio(pideSalir: Boolean, alSalir: () -> Unit, alCancelarSalida: () -> Unit) {
    TemaAirPass {
        Box(modifier = Modifier.fillMaxSize()) {
            var sesion by remember { mutableStateOf<Sesion?>(null) }
            var sinGuardar by remember { mutableStateOf(false) }
            var bloqueada by remember { mutableStateOf(false) }
            var motivo by remember { mutableStateOf<String?>(null) }
            // Acción que espera a que el usuario confirme que descarta sus cambios.
            var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }

            fun cerrarSesion() {
                Portapapeles.borrar()
                sesion?.rapido?.olvidar()
                sesion?.base?.borrarSecretos()
                sesion = null
                sinGuardar = false
                bloqueada = false
            }

            fun intentar(accion: () -> Unit) {
                if (sinGuardar) pendiente = accion else accion()
            }

            fun bloquear() {
                val rapido = sesion?.rapido
                if (rapido == null) {
                    intentar { cerrarSesion() }
                    return
                }
                Portapapeles.borrar()
                rapido.bloquear()
                bloqueada = true
            }

            LaunchedEffect(pideSalir) {
                if (pideSalir) intentar { cerrarSesion(); alSalir() }
            }

            val abierta = sesion
            if (abierta == null) {
                PantallaAbrir(motivo = motivo, alAbrir = { base, fichero, rapido ->
                    motivo = null
                    sesion = Sesion(base, fichero, rapido)
                })
            } else if (bloqueada) {
                PantallaDesbloqueoRapido(
                    rapido = checkNotNull(abierta.rapido),
                    sinGuardar = sinGuardar,
                    alDesbloquear = { bloqueada = false },
                    alCerrar = {
                        motivo = it
                        cerrarSesion()
                    },
                )
            } else {
                PantallaEntradas(
                    sesion = abierta,
                    sinGuardar = sinGuardar,
                    alCambiar = { sinGuardar = true },
                    alGuardar = { sinGuardar = false },
                    alBloquear = { bloquear() },
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
private fun PantallaAbrir(motivo: String?, alAbrir: (BaseDeDatos, File, DesbloqueoRapido?) -> Unit) {
    var fichero by remember { mutableStateOf(UltimaBase.leer()) }
    var error by remember { mutableStateOf(motivo) }
    var estado by remember { mutableStateOf(EstadoAccion.Reposo) }
    val ambito = rememberCoroutineScope()

    fun abrir(caracteres: CharArray) {
        val elegido = fichero ?: return
        error = null
        estado = EstadoAccion.Trabajando
        ambito.launch {
            try {
                // La derivación de la clave tarda a propósito: fuera del hilo de la interfaz.
                val base = withContext(Dispatchers.IO) { abrirKdbx(elegido.readBytes(), caracteres) }
                val codigo = UltimaBase.caracteresRapido()?.let { DesbloqueoRapido.desde(caracteres, it) }
                UltimaBase.recordar(elegido)
                estado = EstadoAccion.Hecho
                delay(650)
                alAbrir(base, elegido, codigo)
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
                if (estado == EstadoAccion.Trabajando) estado = EstadoAccion.Reposo
            }
        }
    }

    PantallaApertura(
        fichero = fichero?.name,
        ruta = fichero?.parent,
        estado = estado,
        error = error,
        alElegir = { elegirFichero()?.let { fichero = it; error = null } },
        alAbrir = ::abrir,
    )
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
    var estadoGuardar by remember { mutableStateOf(EstadoAccion.Reposo) }
    val guardando = estadoGuardar == EstadoAccion.Trabajando
    var borradoPendiente by remember { mutableStateOf<Job?>(null) }
    var edicion by remember { mutableStateOf<Edicion?>(null) }
    var paraBorrar by remember { mutableStateOf<Entrada?>(null) }
    var detalle by remember { mutableStateOf<Entrada?>(null) }
    var opciones by remember { mutableStateOf(false) }
    var rapido by remember { mutableStateOf(sesion.rapido) }
    // La última edición mostrada, para que la pantalla siga pintándose mientras se desliza al cerrarse.
    val mostrada = remember { mutableStateOf<Edicion?>(null) }.apply { if (edicion != null) value = edicion }.value
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
        estadoGuardar = EstadoAccion.Trabajando
        error = null
        ambito.launch {
            try {
                // Guardar vuelve a derivar la clave, y eso tarda: fuera del hilo de la interfaz.
                withContext(Dispatchers.IO) { guardarEnFichero(base, sesion.fichero.toPath()) }
                alGuardar()
                aviso = "Cambios guardados en ${sesion.fichero.name}."
                estadoGuardar = EstadoAccion.Hecho
            } catch (e: Exception) {
                error = "No se han podido guardar los cambios. El fichero no se ha modificado. ${e.message.orEmpty()}"
                estadoGuardar = EstadoAccion.Reposo
                return@launch
            }
            delay(1200)
            estadoGuardar = EstadoAccion.Reposo
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CabeceraBase(
                titulo = sesion.fichero.name,
                subtitulo = "${raiz.totalEntradas()} entradas" + if (sinGuardar) " · cambios sin guardar" else "",
                aviso = aviso,
                error = error,
                estadoGuardar = estadoGuardar,
                // Mientras se guarda no se edita: el documento se está leyendo en otro hilo.
                puedeGuardar = sinGuardar && !guardando,
                alGuardar = { guardar() },
                alOpciones = { opciones = true },
                alBloquear = alBloquear,
            )
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
        PantallaSuperpuesta(visible = edicion != null) {
            mostrada?.let { actual ->
                key(actual) {
                    PantallaEdicion(
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
            }
        }
        PantallaSuperpuesta(visible = opciones) {
            PantallaOpciones(
                base = base,
                rapido = rapido,
                alCambiarRapido = {
                    rapido = it
                    sesion.rapido = it
                    UltimaBase.recordarRapido(it?.caracteres)
                },
                alVolver = { opciones = false },
            )
        }
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
