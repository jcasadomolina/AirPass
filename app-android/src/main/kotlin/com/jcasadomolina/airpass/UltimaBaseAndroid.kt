package com.jcasadomolina.airpass

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

private const val PREFERENCIAS = "airpass"
private const val CLAVE = "ultimaBase"
private const val PERMISOS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

/**
 * Guarda la Uri de la base que se acaba de abrir y pide al sistema que conserve el permiso de
 * lectura y escritura sobre ella después de reiniciar. Libera el permiso de las bases anteriores,
 * para que AirPass solo tenga acceso al fichero que usa. Solo guarda la Uri, nunca la contraseña.
 */
fun recordarUltimaBase(contexto: Context, uri: Uri) {
    val resolver = contexto.contentResolver
    runCatching { resolver.takePersistableUriPermission(uri, PERMISOS) }
    resolver.persistedUriPermissions
        .filter { it.uri != uri }
        .forEach { runCatching { resolver.releasePersistableUriPermission(it.uri, PERMISOS) } }
    contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).edit().putString(CLAVE, uri.toString()).apply()
}

/** Devuelve la última base abierta, o null si no hay ninguna o el sistema ya no da acceso a ella. */
fun leerUltimaBase(contexto: Context): Uri? {
    val uri = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE).getString(CLAVE, null)?.let(Uri::parse)
    return uri?.takeIf { guardada ->
        contexto.contentResolver.persistedUriPermissions.any { it.uri == guardada && it.isReadPermission }
    }
}

/**
 * Ruta legible de un documento elegido con el selector del sistema. Android no da rutas reales:
 * cuando el identificador del documento la contiene (por ejemplo «primary:Download/base.kdbx»),
 * devuelve la parte de la carpeta y el fichero; si no, null.
 */
fun rutaLegible(uri: Uri): String? =
    runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        ?.substringAfter(':', "")
        ?.takeIf { '/' in it }
