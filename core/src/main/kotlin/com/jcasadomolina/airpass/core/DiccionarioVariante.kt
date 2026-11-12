package com.jcasadomolina.airpass.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

// Lee `longitud` bytes. La longitud viene del fichero: se comprueba antes de reservar memoria.
internal fun ByteBuffer.leerBytes(longitud: Int, que: String): ByteArray {
    require(longitud in 0..remaining()) { "$que declara una longitud imposible: $longitud" }
    val valor = ByteArray(longitud)
    get(valor)
    return valor
}

// Formato de KeePass para guardar pares clave-valor con tipo (VariantDictionary).
// Los enteros de 32 bits se devuelven como Int, los de 64 como Long y el resto como ByteArray.
internal fun leerDiccionarioVariante(datos: ByteArray): Map<String, Any> {
    val lector = ByteBuffer.wrap(datos).order(ByteOrder.LITTLE_ENDIAN)
    require(lector.remaining() >= 2) { "El diccionario está vacío" }
    val versionMayor = lector.getShort().toInt() shr 8
    require(versionMayor == 1) { "Versión de diccionario no compatible: $versionMayor" }

    val valores = HashMap<String, Any>()
    while (true) {
        require(lector.remaining() >= 1) { "El diccionario termina sin la marca de fin" }
        val tipo = lector.get().toInt()
        if (tipo == 0) break

        require(lector.remaining() >= 4) { "El diccionario está truncado" }
        val nombre = lector.leerBytes(lector.getInt(), "Un nombre del diccionario").toString(Charsets.UTF_8)
        require(lector.remaining() >= 4) { "El diccionario está truncado" }
        val valor = lector.leerBytes(lector.getInt(), "El valor $nombre del diccionario")

        val numero = ByteBuffer.wrap(valor).order(ByteOrder.LITTLE_ENDIAN)
        valores[nombre] = when {
            (tipo == 0x04 || tipo == 0x0C) && valor.size == 4 -> numero.getInt()
            (tipo == 0x05 || tipo == 0x0D) && valor.size == 8 -> numero.getLong()
            else -> valor
        }
    }
    return valores
}
