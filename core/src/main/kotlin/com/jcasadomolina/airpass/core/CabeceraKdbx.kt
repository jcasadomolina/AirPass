package com.jcasadomolina.airpass.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class Cifrado(val id: String, val longitudIv: Int) {
    AES256("31c1f2e6bf714350be5805216afc5aff", 16),
    CHACHA20("d6038a2b8b6f4cb5a524339a31dbb59a", 12),
}

class CabeceraKdbx(
    val version: VersionKdbx,
    val cifrado: Cifrado,
    val comprimido: Boolean,
    val semillaMaestra: ByteArray,
    val iv: ByteArray,
    // shortcut: diccionario de la KDF sin interpretar, se analizará al derivar la clave
    val parametrosKdf: ByteArray,
    val longitud: Int,
)

private const val FIN = 0
private const val CIFRADO = 2
private const val COMPRESION = 3
private const val SEMILLA_MAESTRA = 4
private const val IV = 7
private const val PARAMETROS_KDF = 11

fun leerCabeceraKdbx(datos: ByteArray): CabeceraKdbx {
    val version = leerVersionKdbx(datos)

    val lector = ByteBuffer.wrap(datos).order(ByteOrder.LITTLE_ENDIAN)
    lector.position(12)

    val campos = HashMap<Int, ByteArray>()
    while (true) {
        require(lector.remaining() >= 5) { "La cabecera termina sin el campo de fin" }
        val tipo = lector.get().toInt()
        val valor = lector.leerBytes(lector.getInt(), "El campo $tipo de la cabecera")
        if (tipo == FIN) break
        campos[tipo] = valor
    }

    fun campo(tipo: Int, nombre: String, longitud: Int? = null): ByteArray {
        val valor = requireNotNull(campos[tipo]) { "Falta el campo obligatorio de la cabecera: $nombre" }
        require(longitud == null || valor.size == longitud) {
            "El campo $nombre mide ${valor.size} bytes y debería medir $longitud"
        }
        return valor
    }

    val idCifrado = campo(CIFRADO, "cifrado", 16).toHexString()
    val cifrado = requireNotNull(Cifrado.entries.find { it.id == idCifrado }) {
        "Algoritmo de cifrado no compatible: $idCifrado"
    }

    val compresion = ByteBuffer.wrap(campo(COMPRESION, "compresión", 4))
        .order(ByteOrder.LITTLE_ENDIAN).getInt()
    require(compresion in 0..1) { "Algoritmo de compresión no compatible: $compresion" }

    return CabeceraKdbx(
        version = version,
        cifrado = cifrado,
        comprimido = compresion == 1,
        semillaMaestra = campo(SEMILLA_MAESTRA, "semilla maestra", 32),
        iv = campo(IV, "vector de inicialización", cifrado.longitudIv),
        parametrosKdf = campo(PARAMETROS_KDF, "parámetros de la KDF"),
        longitud = lector.position(),
    )
}
