package com.jcasadomolina.airpass.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.crypto.engines.ChaCha7539Engine
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV

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
// Usa la implementación de BouncyCastle y no la del sistema: en Android, el ChaCha20 del
// proveedor de la plataforma no continuaba el flujo igual que en el escritorio, y daba
// otras contraseñas. Con la misma implementación en todas partes, el resultado es el mismo.
class FlujoChaCha20(clave: ByteArray, nonce: ByteArray, contador: Int = 0) {
    private val motor = ChaCha7539Engine()

    init {
        require(clave.size == 32) { "La clave de ChaCha20 debe medir 32 bytes" }
        require(nonce.size == 12) { "El nonce de ChaCha20 debe medir 12 bytes" }
        require(contador >= 0) { "Contador de ChaCha20 no válido: $contador" }
        motor.init(true, ParametersWithIV(KeyParameter(clave), nonce))
        // Cada valor del contador corresponde a un bloque de 64 bytes del flujo.
        motor.skip(contador * 64L)
    }

    fun aplicar(datos: ByteArray): ByteArray {
        val salida = ByteArray(datos.size)
        motor.processBytes(datos, 0, datos.size, salida, 0)
        return salida
    }
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

/**
 * Contraseña aleatoria con caracteres de los grupos dados. Si `unoDeCada`, primero se pone un carácter de
 * cada grupo y luego se baraja todo (Fisher-Yates), para que esos caracteres no queden siempre al principio.
 * nextInt(n) elige con la misma probabilidad; hacer byte % n favorecería unos caracteres (sesgo de módulo).
 */
fun contrasenaAleatoria(grupos: List<String>, longitud: Int, unoDeCada: Boolean): CharArray {
    require(grupos.isNotEmpty() && grupos.none { it.isEmpty() }) { "No hay caracteres para generar la contraseña" }
    require(longitud in 1..256) { "Longitud de contraseña no válida: $longitud" }
    require(!unoDeCada || longitud >= grupos.size) { "La longitud es menor que el número de grupos" }
    val todos = grupos.joinToString("")
    val resultado = CharArray(longitud) { todos[generador.nextInt(todos.length)] }
    if (unoDeCada) grupos.forEachIndexed { i, grupo -> resultado[i] = grupo[generador.nextInt(grupo.length)] }
    for (i in resultado.lastIndex downTo 1) {
        val j = generador.nextInt(i + 1)
        resultado[i] = resultado[j].also { resultado[j] = resultado[i] }
    }
    return resultado
}
