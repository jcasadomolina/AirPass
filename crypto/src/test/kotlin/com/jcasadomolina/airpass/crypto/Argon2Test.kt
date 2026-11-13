package com.jcasadomolina.airpass.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Argon2Test {

    // Entradas de los vectores de la RFC 9106, apartado 5.
    private val clave = ByteArray(32) { 0x01 }
    private val sal = ByteArray(16) { 0x02 }
    private val secreto = ByteArray(8) { 0x03 }
    private val datosAsociados = ByteArray(12) { 0x04 }

    private fun vector(tipo: TipoArgon2) = argon2(
        tipo, clave, sal,
        iteraciones = 3, memoriaKiB = 32, paralelismo = 4,
        secreto = secreto, datosAsociados = datosAsociados,
    ).toHexString()

    // RFC 9106, apartado 5.1.
    @Test
    fun argon2dVectorRfc9106() {
        assertEquals("512b391b6f1162975371d30919734294f868e3be3984f3c1a13a4db9fabe4acb", vector(TipoArgon2.D))
    }

    // RFC 9106, apartado 5.3.
    @Test
    fun argon2idVectorRfc9106() {
        assertEquals("0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659", vector(TipoArgon2.ID))
    }

    @Test
    fun rechazaParametrosNoValidos() {
        fun con(iteraciones: Int = 1, memoriaKiB: Int = 64, paralelismo: Int = 1, sal: ByteArray = this.sal) =
            argon2(TipoArgon2.ID, clave, sal, iteraciones, memoriaKiB, paralelismo)

        assertFailsWith<IllegalArgumentException> { con(iteraciones = 0) }
        assertFailsWith<IllegalArgumentException> { con(paralelismo = 0) }
        assertFailsWith<IllegalArgumentException> { con(memoriaKiB = 7) }
        assertFailsWith<IllegalArgumentException> { con(sal = ByteArray(4)) }
    }
}
