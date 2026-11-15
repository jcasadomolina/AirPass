package com.jcasadomolina.airpass

import android.content.ContentResolver
import android.net.Uri
import java.io.FileOutputStream
import java.io.IOException

// Con el Storage Access Framework no hay rutas, así que no se puede escribir en un temporal
// y renombrarlo, como en el escritorio: hay que sobrescribir el fichero. Para no perder datos:
// los bytes llegan ya verificados (guardarVerificado), se fuerza el volcado a disco, se relee lo
// escrito y, si algo falla, se intenta dejar el contenido anterior, que está cifrado.
internal fun escribirEnUri(resolver: ContentResolver, uri: Uri, datos: ByteArray) {
    val anterior = leer(resolver, uri)
    try {
        sobrescribir(resolver, uri, datos)
        require(leer(resolver, uri).contentEquals(datos)) { "Lo escrito no coincide con lo que se quería guardar" }
    } catch (e: Exception) {
        runCatching { sobrescribir(resolver, uri, anterior) }
        throw e
    }
}

private fun leer(resolver: ContentResolver, uri: Uri): ByteArray =
    resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("No se puede leer el fichero")

// "rwt": lectura, escritura y truncado. Algunos proveedores no truncan con "w" y dejarían
// restos del fichero anterior al final; por eso además se trunca a mano.
private fun sobrescribir(resolver: ContentResolver, uri: Uri, datos: ByteArray) {
    val descriptor = resolver.openFileDescriptor(uri, "rwt") ?: throw IOException("No se puede escribir el fichero")
    descriptor.use {
        FileOutputStream(it.fileDescriptor).use { salida ->
            salida.channel.truncate(0)
            salida.write(datos)
            salida.flush()
            salida.fd.sync()
        }
    }
}
