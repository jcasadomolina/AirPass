package com.jcasadomolina.airpass.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrimitivosTest {

    // Vector del NIST (FIPS 180-4) para la entrada "abc".
    @Test
    fun sha512VectorAbc() {
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            sha512("abc".toByteArray()).toHexString(),
        )
    }

    // RFC 4231, caso de prueba 2.
    @Test
    fun hmacSha256VectorRfc4231() {
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            hmacSha256("Jefe".toByteArray(), "what do ya want for nothing?".toByteArray()).toHexString(),
        )
    }

    // AES-KDF no tiene vectores oficiales. Con una ronda equivale a cifrar dos
    // bloques con AES-256, y eso sí tiene vector: FIPS 197, apéndice C.3.
    @Test
    fun aesKdfConUnaRondaCoincideConElVectorDeAes256() {
        val semilla = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".hexToByteArray()
        val bloque = "00112233445566778899aabbccddeeff"
        val cifrado = "8ea2b7ca516745bfeafc49904b496089"

        assertEquals(
            sha256((cifrado + cifrado).hexToByteArray()).toHexString(),
            aesKdf((bloque + bloque).hexToByteArray(), semilla, rondas = 1).toHexString(),
        )
    }

    @Test
    fun aesKdfCambiaConElNumeroDeRondas() {
        val clave = ByteArray(32) { 1 }
        val semilla = ByteArray(32) { 2 }

        assertFalse(iguales(aesKdf(clave, semilla, 1), aesKdf(clave, semilla, 2)))
    }

    @Test
    fun aesKdfRechazaTamanosIncorrectos() {
        assertFailsWith<IllegalArgumentException> { aesKdf(ByteArray(31), ByteArray(32), 1) }
        assertFailsWith<IllegalArgumentException> { aesKdf(ByteArray(32), ByteArray(16), 1) }
        assertFailsWith<IllegalArgumentException> { aesKdf(ByteArray(32), ByteArray(32), -1) }
    }

    // NIST SP 800-38A, F.2.5 (CBC-AES256), primer bloque. El vector no lleva relleno:
    // se compara solo el primer bloque del cifrado y después se descifra de vuelta.
    @Test
    fun aes256CbcCifraYDescifraElVectorDelNist() {
        val clave = "603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4".hexToByteArray()
        val iv = "000102030405060708090a0b0c0d0e0f".hexToByteArray()
        val claro = "6bc1bee22e409f96e93d7e117393172a".hexToByteArray()

        val cifrado = aes256CbcCifrar(clave, iv, claro)

        assertEquals("f58c4c04d6e5f1ba779eabfb5f7bfbd6", cifrado.copyOf(16).toHexString())
        assertEquals(claro.toHexString(), aes256CbcDescifrar(clave, iv, cifrado).toHexString())
    }

    // RFC 8439, apartado 2.4.2: primeros 16 bytes del texto cifrado.
    @Test
    fun chaCha20VectorRfc8439() {
        val clave = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f".hexToByteArray()
        val nonce = "000000000000004a00000000".hexToByteArray()
        val claro = ("Ladies and Gentlemen of the class of '99: If I could offer you " +
            "only one tip for the future, sunscreen would be it.").toByteArray()

        val cifrado = FlujoChaCha20(clave, nonce, contador = 1).aplicar(claro)

        assertEquals("6e2e359a2568f98041ba0728dd0d6981", cifrado.copyOf(16).toHexString())
    }

    @Test
    fun chaCha20ContinuaElFlujoEntreLlamadas() {
        val clave = ByteArray(32) { 7 }
        val nonce = ByteArray(12) { 9 }
        val datos = ByteArray(100) { it.toByte() }

        val deUnaVez = FlujoChaCha20(clave, nonce).aplicar(datos)
        val flujo = FlujoChaCha20(clave, nonce)
        val enDosPartes = flujo.aplicar(datos.copyOf(30)) + flujo.aplicar(datos.copyOfRange(30, 100))

        assertEquals(deUnaVez.toHexString(), enDosPartes.toHexString())
    }

    @Test
    fun bytesAleatoriosDaLaCantidadPedidaYNoSeRepite() {
        val uno = bytesAleatorios(32)
        val otro = bytesAleatorios(32)

        assertEquals(32, uno.size)
        assertFalse(iguales(uno, otro))
    }

    @Test
    fun contrasenaAleatoriaUsaElAlfabetoYNoSeRepite() {
        val una = contrasenaAleatoria()

        assertEquals(20, una.size)
        assertTrue(una.all { it in ALFABETO_CONTRASENAS })
        assertFalse(una.contentEquals(contrasenaAleatoria()))
        assertFailsWith<IllegalArgumentException> { contrasenaAleatoria(4) }
    }

    @Test
    fun igualesComparaElContenido() {
        assertTrue(iguales(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertFalse(iguales(byteArrayOf(1, 2), byteArrayOf(1, 3)))
    }
}
