package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.TipoArgon2
import com.jcasadomolina.airpass.crypto.aesKdf
import com.jcasadomolina.airpass.crypto.argon2
import com.jcasadomolina.airpass.crypto.hmacSha256
import com.jcasadomolina.airpass.crypto.iguales
import com.jcasadomolina.airpass.crypto.sha256
import com.jcasadomolina.airpass.crypto.sha512
import java.nio.CharBuffer

// La contraseña no abre el fichero, o la cabecera ha sido manipulada: no se pueden distinguir.
class ClaveIncorrectaException : Exception("La clave no es correcta o el fichero ha sido manipulado")

class ClavesKdbx(val cifrado: ByteArray, val hmac: ByteArray)

private const val AES_KDF = "c9d9f39a628a4460bf740d08c18a4fea"
private const val ARGON2D = "ef636ddf8c29444b91f7a9a403e30a0c"
private const val ARGON2ID = "9e298b1956db4773b23dfc3ec6f0a1e6"

// Los parámetros de la KDF se leen antes de poder autenticar la cabecera, así que un
// fichero manipulado puede pedir cualquier valor. Sin tope, agotaría la memoria.
// shortcut: límites fijos; hacerlos configurables si un usuario real los supera.
private const val MAX_MEMORIA_ARGON2 = 1_073_741_824uL // 1 GiB
private const val MAX_ITERACIONES_ARGON2 = 100uL
private const val MAX_PARALELISMO_ARGON2 = 64u

// Convierte a UTF-8 sin pasar por String, para que no quede una copia que no se pueda borrar.
internal fun aUtf8(texto: CharArray): ByteArray {
    val buffer = Charsets.UTF_8.encode(CharBuffer.wrap(texto))
    val utf8 = ByteArray(buffer.remaining())
    buffer.get(utf8)
    buffer.array().fill(0)
    return utf8
}

// Clave compuesta de KDBX: SHA-256 del SHA-256 de la contraseña en UTF-8.
// shortcut: solo contraseña, sin fichero clave; añadirlo cuando se implemente RF-01 completo.
internal fun claveCompuesta(contrasena: CharArray): ByteArray {
    val utf8 = aUtf8(contrasena)
    val resumen = sha256(utf8)
    utf8.fill(0)
    val compuesta = sha256(resumen)
    resumen.fill(0)
    return compuesta
}

internal fun transformarClave(compuesta: ByteArray, parametrosKdf: ByteArray): ByteArray {
    val parametros = leerDiccionarioVariante(parametrosKdf)
    return when (val kdf = (parametros["\$UUID"] as? ByteArray)?.toHexString()) {
        AES_KDF -> {
            val semilla = requireNotNull(parametros["S"] as? ByteArray) { "Falta la semilla de AES-KDF" }
            val rondas = requireNotNull(parametros["R"] as? ULong) { "Faltan las rondas de AES-KDF" }
            require(rondas <= Long.MAX_VALUE.toULong()) { "Número de rondas de AES-KDF no válido: $rondas" }
            aesKdf(compuesta, semilla, rondas.toLong())
        }
        ARGON2D, ARGON2ID -> {
            val sal = requireNotNull(parametros["S"] as? ByteArray) { "Falta la sal de Argon2" }
            val memoria = requireNotNull(parametros["M"] as? ULong) { "Falta la memoria de Argon2" }
            val iteraciones = requireNotNull(parametros["I"] as? ULong) { "Faltan las iteraciones de Argon2" }
            val paralelismo = requireNotNull(parametros["P"] as? UInt) { "Falta el paralelismo de Argon2" }
            val version = requireNotNull(parametros["V"] as? UInt) { "Falta la versión de Argon2" }
            require(memoria in 1uL..MAX_MEMORIA_ARGON2) { "El fichero pide una memoria de Argon2 no admitida: $memoria bytes" }
            require(iteraciones in 1uL..MAX_ITERACIONES_ARGON2) { "El fichero pide unas iteraciones de Argon2 no admitidas: $iteraciones" }
            require(paralelismo in 1u..MAX_PARALELISMO_ARGON2) { "El fichero pide un paralelismo de Argon2 no admitido: $paralelismo" }
            argon2(
                tipo = if (kdf == ARGON2D) TipoArgon2.D else TipoArgon2.ID,
                clave = compuesta,
                sal = sal,
                iteraciones = iteraciones.toInt(),
                memoriaKiB = (memoria / 1024uL).toInt(),
                paralelismo = paralelismo.toInt(),
                version = version.toInt(),
                secreto = parametros["K"] as? ByteArray,
                datosAsociados = parametros["A"] as? ByteArray,
            )
        }
        else -> throw IllegalArgumentException("Función de derivación de claves no compatible: $kdf")
    }
}

// Claves finales a partir de la semilla maestra y de la clave transformada.
internal fun clavesFinales(semillaMaestra: ByteArray, transformada: ByteArray): ClavesKdbx {
    val base = semillaMaestra + transformada
    val claves = ClavesKdbx(cifrado = sha256(base), hmac = sha512(base + 0x01))
    base.fill(0)
    return claves
}

// Deriva las claves del fichero y comprueba con ellas la cabecera.
// Lanza ClaveIncorrectaException si la contraseña no es la del fichero.
fun derivarClaves(datos: ByteArray, cabecera: CabeceraKdbx, contrasena: CharArray): ClavesKdbx {
    val compuesta = claveCompuesta(contrasena)
    try {
        return derivarClavesConCompuesta(datos, cabecera, compuesta)
    } finally {
        compuesta.fill(0)
    }
}

// Igual que derivarClaves, a partir de la clave compuesta. No la borra: es de quien llama.
internal fun derivarClavesConCompuesta(datos: ByteArray, cabecera: CabeceraKdbx, compuesta: ByteArray): ClavesKdbx {
    val fin = cabecera.longitud
    require(datos.size >= fin + 64) { "El fichero termina antes de la verificación de la cabecera" }
    val bytesCabecera = datos.copyOf(fin)
    require(iguales(sha256(bytesCabecera), datos.copyOfRange(fin, fin + 32))) {
        "La cabecera del fichero está dañada"
    }

    val transformada = transformarClave(compuesta, cabecera.parametrosKdf)
    val claves = clavesFinales(cabecera.semillaMaestra, transformada)
    transformada.fill(0)

    // La cabecera se autentica con el bloque de índice 2^64 - 1 (ocho bytes 0xFF).
    val claveCabecera = claveDeBloque(-1, claves.hmac)
    val correcta = iguales(hmacSha256(claveCabecera, bytesCabecera), datos.copyOfRange(fin + 32, fin + 64))
    claveCabecera.fill(0)

    if (!correcta) {
        claves.cifrado.fill(0)
        claves.hmac.fill(0)
        throw ClaveIncorrectaException()
    }
    return claves
}
