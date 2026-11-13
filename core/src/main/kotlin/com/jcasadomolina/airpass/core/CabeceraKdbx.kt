package com.jcasadomolina.airpass.core

import java.io.ByteArrayOutputStream
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
    // Diccionario de la KDF tal como viene en el fichero; lo interpreta transformarClave.
    val parametrosKdf: ByteArray,
    // Datos de otros programas (campo 12). AirPass no los interpreta, pero los conserva.
    val datosPublicos: ByteArray?,
    val longitud: Int,
)

private const val FIN = 0
private const val CIFRADO = 2
private const val COMPRESION = 3
private const val SEMILLA_MAESTRA = 4
private const val IV = 7
private const val PARAMETROS_KDF = 11
private const val DATOS_PUBLICOS = 12

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
        datosPublicos = campos[DATOS_PUBLICOS],
        longitud = lector.position(),
    )
}

// Escribe una cabecera nueva. Mismo orden de campos que KeePass.
internal fun escribirCabeceraKdbx(
    version: VersionKdbx,
    cifrado: Cifrado,
    comprimido: Boolean,
    semillaMaestra: ByteArray,
    iv: ByteArray,
    parametrosKdf: ByteArray,
    datosPublicos: ByteArray?,
): ByteArray {
    val salida = ByteArrayOutputStream()
    fun campo(tipo: Int, valor: ByteArray) {
        salida.write(tipo)
        salida.write(enteroLe(valor.size))
        salida.write(valor)
    }

    salida.write(enteroLe(0x9AA2D903.toInt()))
    salida.write(enteroLe(0xB54BFB67.toInt()))
    salida.write(enteroLe((version.mayor shl 16) or version.menor))
    campo(CIFRADO, cifrado.id.hexToByteArray())
    campo(COMPRESION, enteroLe(if (comprimido) 1 else 0))
    campo(SEMILLA_MAESTRA, semillaMaestra)
    campo(IV, iv)
    campo(PARAMETROS_KDF, parametrosKdf)
    if (datosPublicos != null) campo(DATOS_PUBLICOS, datosPublicos)
    campo(FIN, byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A))
    return salida.toByteArray()
}
