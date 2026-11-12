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

    @Test
    fun igualesComparaElContenido() {
        assertTrue(iguales(byteArrayOf(1, 2), byteArrayOf(1, 2)))
        assertFalse(iguales(byteArrayOf(1, 2), byteArrayOf(1, 3)))
    }
}
