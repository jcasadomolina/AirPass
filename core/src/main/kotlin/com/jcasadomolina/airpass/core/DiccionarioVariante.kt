package com.jcasadomolina.airpass.core

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Lee `longitud` bytes. La longitud viene del fichero: se comprueba antes de reservar memoria.
internal fun ByteBuffer.leerBytes(longitud: Int, que: String): ByteArray {
    require(longitud in 0..remaining()) { "$que declara una longitud imposible: $longitud" }
    val valor = ByteArray(longitud)
    get(valor)
    return valor
}

internal fun enteroLe(valor: Int): ByteArray =
    ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(valor).array()

internal fun enteroLe(valor: Long): ByteArray =
    ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(valor).array()

// Formato de KeePass para guardar pares clave-valor con tipo (VariantDictionary).
// Cada tipo del formato se lee como un tipo distinto de Kotlin, para poder volver a
// escribir el diccionario exactamente igual:
//   0x04 UInt · 0x05 ULong · 0x08 Boolean · 0x0C Int · 0x0D Long · 0x18 String · 0x42 ByteArray
internal fun leerDiccionarioVariante(datos: ByteArray): Map<String, Any> {
    val lector = ByteBuffer.wrap(datos).order(ByteOrder.LITTLE_ENDIAN)
    require(lector.remaining() >= 2) { "El diccionario está vacío" }
    val versionMayor = lector.getShort().toInt() shr 8
    require(versionMayor == 1) { "Versión de diccionario no compatible: $versionMayor" }

    val valores = LinkedHashMap<String, Any>()
    while (true) {
        require(lector.remaining() >= 1) { "El diccionario termina sin la marca de fin" }
        val tipo = lector.get().toInt()
        if (tipo == 0) break

        require(lector.remaining() >= 4) { "El diccionario está truncado" }
        val nombre = lector.leerBytes(lector.getInt(), "Un nombre del diccionario").toString(Charsets.UTF_8)
        require(lector.remaining() >= 4) { "El diccionario está truncado" }
        val valor = lector.leerBytes(lector.getInt(), "El valor $nombre del diccionario")

        fun medida(bytes: Int) = require(valor.size == bytes) {
            "El valor $nombre del diccionario mide ${valor.size} bytes y debería medir $bytes"
        }
        val numero = ByteBuffer.wrap(valor).order(ByteOrder.LITTLE_ENDIAN)
        valores[nombre] = when (tipo) {
            0x04 -> { medida(4); numero.getInt().toUInt() }
            0x05 -> { medida(8); numero.getLong().toULong() }
            0x08 -> { medida(1); valor[0] != 0.toByte() }
            0x0C -> { medida(4); numero.getInt() }
            0x0D -> { medida(8); numero.getLong() }
            0x18 -> valor.toString(Charsets.UTF_8)
            0x42 -> valor
            else -> throw IllegalArgumentException("Tipo de valor desconocido en el diccionario: $tipo")
        }
    }
    return valores
}

internal fun escribirDiccionarioVariante(valores: Map<String, Any>): ByteArray {
    val salida = ByteArrayOutputStream()
    salida.write(byteArrayOf(0x00, 0x01)) // versión 1.0
    for ((nombre, valor) in valores) {
        val (tipo, bytes) = when (valor) {
            is UInt -> 0x04 to enteroLe(valor.toInt())
            is ULong -> 0x05 to enteroLe(valor.toLong())
            is Boolean -> 0x08 to byteArrayOf(if (valor) 1 else 0)
            is Int -> 0x0C to enteroLe(valor)
            is Long -> 0x0D to enteroLe(valor)
            is String -> 0x18 to valor.toByteArray()
            is ByteArray -> 0x42 to valor
            else -> throw IllegalArgumentException("Tipo de valor no admitido en el diccionario: $nombre")
        }
        val nombreUtf8 = nombre.toByteArray()
        salida.write(tipo)
        salida.write(enteroLe(nombreUtf8.size))
        salida.write(nombreUtf8)
        salida.write(enteroLe(bytes.size))
        salida.write(bytes)
    }
    salida.write(0)
    return salida.toByteArray()
}
