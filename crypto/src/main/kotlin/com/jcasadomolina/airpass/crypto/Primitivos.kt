package com.jcasadomolina.airpass.crypto

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun sha512(datos: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-512").digest(datos)

fun hmacSha256(clave: ByteArray, datos: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(clave, "HmacSHA256"))
    return mac.doFinal(datos)
}

// Comparación en tiempo constante: no revela en qué byte difieren los valores.
fun iguales(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

// AES-KDF de KeePass: cifra la clave con AES-256 en modo ECB tantas veces
// como indiquen las rondas, usando la semilla como clave, y resume el resultado.
fun aesKdf(clave: ByteArray, semilla: ByteArray, rondas: Long): ByteArray {
    require(clave.size == 32) { "La clave de AES-KDF debe medir 32 bytes" }
    require(semilla.size == 32) { "La semilla de AES-KDF debe medir 32 bytes" }
    require(rondas >= 0) { "Número de rondas no válido: $rondas" }

    val aes = Cipher.getInstance("AES/ECB/NoPadding")
    aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(semilla, "AES"))

    val bloque = clave.copyOf()
    for (ronda in 0 until rondas) aes.update(bloque, 0, 32, bloque, 0)

    val transformada = sha256(bloque)
    bloque.fill(0)
    return transformada
}
