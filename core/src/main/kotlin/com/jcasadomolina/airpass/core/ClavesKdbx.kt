package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.aesKdf
import com.jcasadomolina.airpass.crypto.hmacSha256
import com.jcasadomolina.airpass.crypto.iguales
import com.jcasadomolina.airpass.crypto.sha256
import com.jcasadomolina.airpass.crypto.sha512
import java.nio.CharBuffer

// La contraseña no abre el fichero, o la cabecera ha sido manipulada: no se pueden distinguir.
class ClaveIncorrectaException : Exception("La clave no es correcta o el fichero ha sido manipulado")

class ClavesKdbx(val cifrado: ByteArray, val hmac: ByteArray)

private const val AES_KDF = "c9d9f39a628a4460bf740d08c18a4fea"

// Clave compuesta de KDBX: SHA-256 del SHA-256 de la contraseña en UTF-8.
// shortcut: solo contraseña, sin fichero clave; añadirlo cuando se implemente RF-01 completo.
internal fun claveCompuesta(contrasena: CharArray): ByteArray {
    val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(contrasena))
    val utf8 = ByteArray(buffer.remaining())
    buffer.get(utf8)
    buffer.array().fill(0)

    val resumen = sha256(utf8)
    utf8.fill(0)
    val compuesta = sha256(resumen)
    resumen.fill(0)
    return compuesta
}

internal fun transformarClave(compuesta: ByteArray, parametrosKdf: ByteArray): ByteArray {
    val parametros = leerDiccionarioVariante(parametrosKdf)
    val kdf = (parametros["\$UUID"] as? ByteArray)?.toHexString()
    // shortcut: solo AES-KDF; Argon2d/id necesitan BouncyCastle y un fichero de prueba.
    require(kdf == AES_KDF) { "Función de derivación de claves no compatible: $kdf" }

    val semilla = requireNotNull(parametros["S"] as? ByteArray) { "Falta la semilla de AES-KDF" }
    val rondas = requireNotNull(parametros["R"] as? Long) { "Faltan las rondas de AES-KDF" }
    return aesKdf(compuesta, semilla, rondas)
}

// Deriva las claves del fichero y comprueba con ellas la cabecera.
// Lanza ClaveIncorrectaException si la contraseña no es la del fichero.
fun derivarClaves(datos: ByteArray, cabecera: CabeceraKdbx, contrasena: CharArray): ClavesKdbx {
    val fin = cabecera.longitud
    require(datos.size >= fin + 64) { "El fichero termina antes de la verificación de la cabecera" }
    val bytesCabecera = datos.copyOf(fin)
    require(iguales(sha256(bytesCabecera), datos.copyOfRange(fin, fin + 32))) {
        "La cabecera del fichero está dañada"
    }

    val compuesta = claveCompuesta(contrasena)
    val transformada = transformarClave(compuesta, cabecera.parametrosKdf)
    compuesta.fill(0)

    val base = cabecera.semillaMaestra + transformada
    transformada.fill(0)
    val claves = ClavesKdbx(cifrado = sha256(base), hmac = sha512(base + 0x01))
    base.fill(0)

    // La cabecera se autentica con el bloque de índice 2^64 - 1 (ocho bytes 0xFF).
    val claveCabecera = sha512(ByteArray(8) { 0xFF.toByte() } + claves.hmac)
    val correcta = iguales(hmacSha256(claveCabecera, bytesCabecera), datos.copyOfRange(fin + 32, fin + 64))
    claveCabecera.fill(0)

    if (!correcta) {
        claves.cifrado.fill(0)
        claves.hmac.fill(0)
        throw ClaveIncorrectaException()
    }
    return claves
}
