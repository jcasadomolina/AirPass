package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.FlujoChaCha20
import com.jcasadomolina.airpass.crypto.aes256CbcDescifrar
import com.jcasadomolina.airpass.crypto.hmacSha256
import com.jcasadomolina.airpass.crypto.iguales
import com.jcasadomolina.airpass.crypto.sha512
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

// La contraseña ya se ha comprobado con la cabecera: un bloque que no cuadra es un fichero alterado.
class FicheroManipuladoException : Exception("El contenido del fichero ha sido modificado o está dañado")

// Contenido descifrado. El XML lleva los campos protegidos (las contraseñas) cifrados
// todavía con el cifrado interno, cuya clave es `claveInterna`.
class ContenidoKdbx(
    val cifradoInterno: Int,
    val claveInterna: ByteArray,
    val xml: ByteArray,
    // Adjuntos de la cabecera interna. AirPass no los muestra todavía, pero los conserva.
    val adjuntos: List<ByteArray> = emptyList(),
)

// Clave del HMAC de cada bloque: SHA-512(índice ‖ clave de HMAC). El índice -1 es el de la cabecera.
internal fun claveDeBloque(indice: Long, claveHmac: ByteArray): ByteArray {
    val entrada = ByteBuffer.allocate(8 + claveHmac.size).order(ByteOrder.LITTLE_ENDIAN)
        .putLong(indice).put(claveHmac).array()
    val clave = sha512(entrada)
    entrada.fill(0)
    return clave
}

// Recorre los bloques del contenido, comprueba el HMAC de cada uno y devuelve los datos unidos.
internal fun leerBloquesAutenticados(datos: ByteArray, inicio: Int, claveHmac: ByteArray): ByteArray {
    val lector = ByteBuffer.wrap(datos).order(ByteOrder.LITTLE_ENDIAN)
    lector.position(inicio)

    val salida = ByteArrayOutputStream()
    var indice = 0L
    while (true) {
        // Sin el bloque final vacío, alguien ha recortado el fichero.
        require(lector.remaining() >= 36) { "El fichero termina antes del bloque final" }
        val hmacGuardado = lector.leerBytes(32, "El HMAC del bloque $indice")
        val longitud = lector.getInt()
        val bloque = lector.leerBytes(longitud, "El bloque $indice")

        val autenticado = ByteBuffer.allocate(12 + longitud).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(indice).putInt(longitud).put(bloque).array()
        val clave = claveDeBloque(indice, claveHmac)
        val correcto = iguales(hmacSha256(clave, autenticado), hmacGuardado)
        clave.fill(0)
        if (!correcto) throw FicheroManipuladoException()

        if (longitud == 0) break
        salida.write(bloque)
        indice++
    }
    return salida.toByteArray()
}

// Trocea los datos en bloques y antepone a cada uno su HMAC. Termina con un bloque vacío.
internal fun escribirBloquesAutenticados(
    datos: ByteArray,
    claveHmac: ByteArray,
    tamanoBloque: Int = 1024 * 1024,
): ByteArray {
    val salida = ByteArrayOutputStream()
    var indice = 0L
    var posicion = 0
    while (true) {
        val longitud = minOf(tamanoBloque, datos.size - posicion)
        val autenticado = ByteBuffer.allocate(12 + longitud).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(indice).putInt(longitud).put(datos, posicion, longitud).array()
        val clave = claveDeBloque(indice, claveHmac)
        salida.write(hmacSha256(clave, autenticado))
        clave.fill(0)
        salida.write(enteroLe(longitud))
        salida.write(datos, posicion, longitud)

        if (longitud == 0) break
        posicion += longitud
        indice++
    }
    return salida.toByteArray()
}

fun descifrarKdbx(datos: ByteArray, cabecera: CabeceraKdbx, claves: ClavesKdbx): ContenidoKdbx {
    // Primero se autentica y solo después se descifra.
    val cifrado = leerBloquesAutenticados(datos, cabecera.longitud + 64, claves.hmac)
    val descifrado = when (cabecera.cifrado) {
        Cifrado.AES256 -> aes256CbcDescifrar(claves.cifrado, cabecera.iv, cifrado)
        Cifrado.CHACHA20 -> FlujoChaCha20(claves.cifrado, cabecera.iv).aplicar(cifrado)
    }
    val claro =
        if (cabecera.comprimido) {
            // shortcut: sin tope de tamaño al descomprimir; el contenido está autenticado,
            // así que solo quien conoce la clave podría preparar una bomba de compresión.
            val descomprimido = GZIPInputStream(descifrado.inputStream()).use { it.readBytes() }
            descifrado.fill(0)
            descomprimido
        } else {
            descifrado
        }

    // Cabecera interna: mismos campos TLV que la externa, pero ya dentro de lo cifrado.
    val lector = ByteBuffer.wrap(claro).order(ByteOrder.LITTLE_ENDIAN)
    var cifradoInterno: Int? = null
    var claveInterna: ByteArray? = null
    val adjuntos = ArrayList<ByteArray>()
    while (true) {
        require(lector.remaining() >= 5) { "La cabecera interna termina sin el campo de fin" }
        val tipo = lector.get().toInt()
        val valor = lector.leerBytes(lector.getInt(), "El campo $tipo de la cabecera interna")
        if (tipo == 0) break
        when (tipo) {
            1 -> {
                require(valor.size == 4) { "El identificador del cifrado interno debe medir 4 bytes" }
                cifradoInterno = ByteBuffer.wrap(valor).order(ByteOrder.LITTLE_ENDIAN).getInt()
            }
            2 -> claveInterna = valor
            3 -> adjuntos.add(valor)
        }
    }

    val contenido = ContenidoKdbx(
        cifradoInterno = requireNotNull(cifradoInterno) { "Falta el cifrado interno" },
        claveInterna = requireNotNull(claveInterna) { "Falta la clave del cifrado interno" },
        xml = claro.copyOfRange(lector.position(), claro.size),
        adjuntos = adjuntos,
    )
    claro.fill(0)
    return contenido
}
