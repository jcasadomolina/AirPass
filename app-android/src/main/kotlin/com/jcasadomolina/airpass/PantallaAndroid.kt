package com.jcasadomolina.airpass

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.BaseDeDatos
import com.jcasadomolina.airpass.core.ClaveIncorrectaException
import com.jcasadomolina.airpass.core.DesbloqueoRapido
import com.jcasadomolina.airpass.core.Entrada
import com.jcasadomolina.airpass.core.FicheroManipuladoException
import com.jcasadomolina.airpass.core.Grupo
import com.jcasadomolina.airpass.core.abrirKdbx
import com.jcasadomolina.airpass.core.abrirKdbxConClave
import com.jcasadomolina.airpass.core.anadirEntrada
import com.jcasadomolina.airpass.core.borrarEntrada
import com.jcasadomolina.airpass.core.guardarVerificado
import com.jcasadomolina.airpass.core.modificarEntrada
import com.jcasadomolina.airpass.core.restaurarVersionAnterior
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// En Android no hay rutas: el fichero se identifica por una Uri que da el selector del sistema.
private class SesionAndroid(val base: BaseDeDatos, val uri: Uri, val nombre: String, var rapido: DesbloqueoRapido?)

@Composable
fun PantallaAndroid() {
    TemaAirPass {
        Box(modifier = Modifier.fillMaxSize()) {
            val contexto = LocalContext.current
            val ambito = rememberCoroutineScope()
            var sesion by remember { mutableStateOf<SesionAndroid?>(null) }
            var borradoPendiente by remember { mutableStateOf<Job?>(null) }
            var sinGuardar by remember { mutableStateOf(false) }
            var bloqueada by remember { mutableStateOf(false) }
            var motivo by remember { mutableStateOf<String?>(null) }
            // Acción que espera a que el usuario confirme que descarta sus cambios.
            var pendiente by remember { mutableStateOf<(() -> Unit)?>(null) }

            fun cerrar() {
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
                    intentar { cerrar() }
                    return
                }
                rapido.bloquear()
                bloqueada = true
            }

            // Con una base abierta, «atrás» bloquea en lugar de cerrar la app sin avisar.
            BackHandler(enabled = sesion != null && !bloqueada) { bloquear() }

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
                    PantallaAbrir(motivo = motivo, alAbrir = {
                        motivo = null
                        sesion = it
                    })
                } else if (bloqueada) {
                    PantallaDesbloqueoRapido(
                        rapido = checkNotNull(abierta.rapido),
                        sinGuardar = sinGuardar,
                        alDesbloquear = { bloqueada = false },
                        alCerrar = {
                            motivo = it
                            cerrar()
                        },
                    )
                } else {
                    PantallaEntradas(
                        sesion = abierta,
                        sinGuardar = sinGuardar,
                        alCambiar = { sinGuardar = true },
                        alGuardar = { sinGuardar = false },
                        alCopiar = { copiar(it) },
                        alBloquear = { bloquear() },
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
private fun PantallaAbrir(motivo: String?, alAbrir: (SesionAndroid) -> Unit) {
    val contexto = LocalContext.current
    var uri by remember { mutableStateOf(leerUltimaBase(contexto)) }
    var nombre by remember { mutableStateOf(uri?.let { nombreDe(contexto, it) }) }
    var error by remember { mutableStateOf(motivo) }
    var estado by remember { mutableStateOf(EstadoAccion.Reposo) }
    val ambito = rememberCoroutineScope()
    var huella by remember(uri) { mutableStateOf(uri?.let { tieneHuella(contexto, it) } == true) }

    // El selector de documentos del sistema: AirPass solo recibe acceso al fichero elegido,
    // sin pedir permiso para leer todo el almacenamiento.
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { elegido ->
        if (elegido != null) {
            uri = elegido
            nombre = nombreDe(contexto, elegido)
            error = null
        }
    }

    suspend fun leerDatos(elegido: Uri): ByteArray = withContext(Dispatchers.IO) {
        contexto.contentResolver.openInputStream(elegido)?.use { it.readBytes() } ?: throw IOException("No se puede leer el fichero")
    }

    fun lanzar(accion: suspend () -> Unit) {
        error = null
        estado = EstadoAccion.Trabajando
        ambito.launch {
            try {
                accion()
            } catch (e: ClaveIncorrectaException) {
                error = "La contraseña no es correcta o el fichero ha sido manipulado."
            } catch (e: FicheroManipuladoException) {
                error = "El contenido del fichero ha sido modificado o está dañado."
            } catch (e: IllegalArgumentException) {
                error = "No se puede abrir el fichero. ${e.message}"
            } catch (e: IOException) {
                error = "No se puede leer el fichero."
            } catch (e: GeneralSecurityException) {
                olvidarHuella(contexto)
                huella = false
                error = "La huella ya no es válida, por ejemplo porque se ha añadido otra al dispositivo. " +
                    "Abre con la contraseña y vuelve a activarla."
            } finally {
                if (estado == EstadoAccion.Trabajando) estado = EstadoAccion.Reposo
            }
        }
    }

    suspend fun entrar(sesion: SesionAndroid) {
        recordarUltimaBase(contexto, sesion.uri)
        estado = EstadoAccion.Hecho
        delay(650)
        alAbrir(sesion)
    }

    fun abrir(caracteres: CharArray) {
        val elegido = uri ?: return
        lanzar {
            try {
                // La derivación de la clave tarda a propósito: fuera del hilo de la interfaz.
                val datos = leerDatos(elegido)
                val base = withContext(Dispatchers.IO) { abrirKdbx(datos, caracteres) }
                val codigo = caracteresRapido(contexto)?.let { DesbloqueoRapido.desde(caracteres, it) }
                entrar(SesionAndroid(base, elegido, nombre ?: "Base de datos", codigo))
            } finally {
                caracteres.fill('\u0000')
            }
        }
    }

    fun abrirConHuella() {
        val elegido = uri ?: return
        if (estado != EstadoAccion.Reposo || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        lanzar {
            val datos = leerDatos(elegido)
            val compuesta = leerHuella(contexto) ?: return@lanzar
            try {
                val base = withContext(Dispatchers.IO) { abrirKdbxConClave(datos, compuesta) }
                entrar(SesionAndroid(base, elegido, nombre ?: "Base de datos", null))
            } catch (e: ClaveIncorrectaException) {
                olvidarHuella(contexto)
                huella = false
                error = "La contraseña de la base ha cambiado desde que activaste la huella. Ábrela con la contraseña."
            }
        }
    }

    PantallaApertura(
        fichero = nombre ?: uri?.let { "Base de datos" },
        ruta = uri?.let(::rutaLegible),
        estado = estado,
        error = error,
        alElegir = { elegir.launch(arrayOf("*/*")) },
        alAbrir = ::abrir,
        acciones = {
            if (huella && estado == EstadoAccion.Reposo) {
                OutlinedButton(onClick = { abrirConHuella() }) {
                    Icon(Iconos.Huella, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir con huella")
                }
            }
        },
    )
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
    val biometria = remember { biometriaDisponible(contexto) }
    var huella by remember { mutableStateOf(tieneHuella(contexto, sesion.uri)) }
    var opciones by remember { mutableStateOf(false) }
    var rapido by remember { mutableStateOf(sesion.rapido) }
    var edicion by remember { mutableStateOf<Edicion?>(null) }
    // La última edición mostrada, para que la pantalla siga pintándose mientras se desliza al cerrarse.
    val mostrada = remember { mutableStateOf<Edicion?>(null) }.apply { if (edicion != null) value = edicion }.value
    var paraBorrar by remember { mutableStateOf<Entrada?>(null) }
    var estadoGuardar by remember { mutableStateOf(EstadoAccion.Reposo) }
    val guardando = estadoGuardar == EstadoAccion.Trabajando
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
        estadoGuardar = EstadoAccion.Trabajando
        error = null
        ambito.launch {
            try {
                // Derivar la clave y verificar tarda: fuera del hilo de la interfaz.
                withContext(Dispatchers.IO) {
                    escribirEnUri(contexto.contentResolver, sesion.uri, guardarVerificado(base))
                }
                alGuardar()
                aviso = "Cambios guardados."
                estadoGuardar = EstadoAccion.Hecho
            } catch (e: Exception) {
                error = "No se han podido guardar los cambios. ${e.message.orEmpty()}"
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
                titulo = sesion.nombre,
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
                    recordarRapido(contexto, it?.caracteres)
                },
                alVolver = { opciones = false },
                extras = {
                    if (biometria) {
                        TarjetaDesplegable(
                            titulo = "Desbloqueo con huella",
                            desplegada = huella,
                            alCambiar = { encender ->
                                if (!encender) {
                                    olvidarHuella(contexto)
                                    huella = false
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    ambito.launch {
                                        val compuesta = base.copiaClaveCompuesta()
                                        try {
                                            huella = runCatching { guardarHuella(contexto, sesion.uri, compuesta) }.getOrDefault(false)
                                        } finally {
                                            compuesta.fill(0)
                                        }
                                    }
                                }
                            },
                            conInterruptor = true,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            NotaOpcion(
                                "Abre esta base con la huella desde la pantalla de apertura. Se desactiva sola si se añade " +
                                    "otra huella al dispositivo.",
                            )
                        }
                    }
                },
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
}
