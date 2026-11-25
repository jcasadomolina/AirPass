package com.jcasadomolina.airpass

import java.io.File
import java.util.prefs.Preferences

/**
 * Recuerda la ruta de la última base de datos abierta, en las preferencias del usuario
 * (en Windows, su rama del registro). Solo guarda la ruta, nunca la contraseña ni la clave.
 */
object UltimaBase {
    private const val CLAVE = "ultimaBase"
    private const val CLAVE_RAPIDO = "caracteresRapido"
    private val preferencias = Preferences.userRoot().node("com/jcasadomolina/airpass")

    /** Devuelve la última base abierta, o null si no hay ninguna o el fichero ya no existe. */
    fun leer(): File? = preferencias.get(CLAVE, null)?.let(::File)?.takeIf { it.isFile }

    /** Guarda la ruta absoluta del fichero que se acaba de abrir. */
    fun recordar(fichero: File) {
        preferencias.put(CLAVE, fichero.absolutePath)
        preferencias.flush()
    }

    /** Cuántos caracteres usa el desbloqueo rápido, o null si está desactivado. Es solo la preferencia: el código vive en memoria. */
    fun caracteresRapido(): Int? = preferencias.getInt(CLAVE_RAPIDO, 0).takeIf { it in 1..16 }

    /** Guarda la preferencia del desbloqueo rápido para las próximas aperturas; null la desactiva. */
    fun recordarRapido(caracteres: Int?) {
        if (caracteres == null) preferencias.remove(CLAVE_RAPIDO) else preferencias.putInt(CLAVE_RAPIDO, caracteres)
        preferencias.flush()
    }
}
