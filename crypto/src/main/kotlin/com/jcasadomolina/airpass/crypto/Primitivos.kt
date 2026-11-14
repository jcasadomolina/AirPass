package com.jcasadomolina.airpass.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.ChaCha20ParameterSpec
import javax.crypto.spec.IvParameterSpec
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

// AES-256 en modo CBC con relleno PKCS#7. Los datos deben estar ya autenticados:
// descifrar sin verificar antes el HMAC expone a ataques de relleno (padding oracle).
fun aes256CbcDescifrar(clave: ByteArray, iv: ByteArray, datos: ByteArray): ByteArray {
    require(clave.size == 32) { "La clave de AES-256 debe medir 32 bytes" }
    require(iv.size == 16) { "El vector de inicialización de AES-CBC debe medir 16 bytes" }

    val aes = Cipher.getInstance("AES/CBC/PKCS5Padding")
    aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(clave, "AES"), IvParameterSpec(iv))
    return aes.doFinal(datos)
}

// Cifrado de flujo ChaCha20 (RFC 8439). Cifrar y descifrar son la misma operación.
// Guarda la posición: cada llamada continúa el flujo donde lo dejó la anterior.
class FlujoChaCha20(clave: ByteArray, nonce: ByteArray, contador: Int = 0) {
    private val cifrador = Cipher.getInstance("ChaCha20")

    init {
        require(clave.size == 32) { "La clave de ChaCha20 debe medir 32 bytes" }
        require(nonce.size == 12) { "El nonce de ChaCha20 debe medir 12 bytes" }
        cifrador.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clave, "ChaCha20"), ChaCha20ParameterSpec(nonce, contador))
    }

    fun aplicar(datos: ByteArray): ByteArray = cifrador.update(datos) ?: ByteArray(0)
}

private val generador = SecureRandom()

// Bytes de un generador criptográfico: impredecibles aunque se conozcan los anteriores.
// java.util.Random no sirve para esto, porque su secuencia se puede reconstruir.
fun bytesAleatorios(cantidad: Int): ByteArray {
    val bytes = ByteArray(cantidad)
    generador.nextBytes(bytes)
    return bytes
}

// El vector de inicialización debe ser nuevo y aleatorio en cada cifrado con la misma clave.
fun aes256CbcCifrar(clave: ByteArray, iv: ByteArray, datos: ByteArray): ByteArray {
    require(clave.size == 32) { "La clave de AES-256 debe medir 32 bytes" }
    require(iv.size == 16) { "El vector de inicialización de AES-CBC debe medir 16 bytes" }

    val aes = Cipher.getInstance("AES/CBC/PKCS5Padding")
    aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clave, "AES"), IvParameterSpec(iv))
    return aes.doFinal(datos)
}

internal const val ALFABETO_CONTRASENAS =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%&*+-=?_.:;"

// Contraseña aleatoria. Con 20 caracteres de un alfabeto de 76 son unos 125 bits.
// nextInt(n) elige cada carácter con la misma probabilidad. Hacer byte % n no lo haría:
// unos caracteres saldrían más que otros (sesgo de módulo).
fun contrasenaAleatoria(longitud: Int = 20): CharArray {
    require(longitud in 8..256) { "Longitud de contraseña no válida: $longitud" }
    return CharArray(longitud) { ALFABETO_CONTRASENAS[generador.nextInt(ALFABETO_CONTRASENAS.length)] }
}
