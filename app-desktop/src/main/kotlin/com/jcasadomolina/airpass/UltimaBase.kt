package com.jcasadomolina.airpass

import java.io.File
import java.util.prefs.Preferences

/**
 * Recuerda la ruta de la última base de datos abierta, en las preferencias del usuario
 * (en Windows, su rama del registro). Solo guarda la ruta, nunca la contraseña ni la clave.
 */
object UltimaBase {
    private const val CLAVE = "ultimaBase"
    private val preferencias = Preferences.userRoot().node("com/jcasadomolina/airpass")

    /** Devuelve la última base abierta, o null si no hay ninguna o el fichero ya no existe. */
    fun leer(): File? = preferencias.get(CLAVE, null)?.let(::File)?.takeIf { it.isFile }

    /** Guarda la ruta absoluta del fichero que se acaba de abrir. */
    fun recordar(fichero: File) {
        preferencias.put(CLAVE, fichero.absolutePath)
        preferencias.flush()
    }
}
