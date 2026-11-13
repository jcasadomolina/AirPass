package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.TipoArgon2
import com.jcasadomolina.airpass.crypto.argon2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Argon2KdbxTest {

    private val argon2d = "ef636ddf8c29444b91f7a9a403e30a0c".hexToByteArray()
    private val argon2id = "9e298b1956db4773b23dfc3ec6f0a1e6".hexToByteArray()
    private val compuesta = ByteArray(32) { 5 }
    private val sal = ByteArray(32) { 6 }

    private fun parametros(
        uuid: ByteArray = argon2id,
        memoria: ULong = 64uL * 1024uL,
        iteraciones: ULong = 2uL,
        paralelismo: UInt = 2u,
    ) = escribirDiccionarioVariante(
        linkedMapOf("\$UUID" to uuid, "S" to sal, "M" to memoria, "I" to iteraciones, "P" to paralelismo, "V" to 0x13u),
    )

    @Test
    fun usaArgon2idConLosParametrosDelFichero() {
        assertEquals(
            argon2(TipoArgon2.ID, compuesta, sal, iteraciones = 2, memoriaKiB = 64, paralelismo = 2).toHexString(),
            transformarClave(compuesta, parametros()).toHexString(),
        )
    }

    @Test
    fun distingueArgon2dDeArgon2id() {
        assertEquals(
            argon2(TipoArgon2.D, compuesta, sal, iteraciones = 2, memoriaKiB = 64, paralelismo = 2).toHexString(),
            transformarClave(compuesta, parametros(uuid = argon2d)).toHexString(),
        )
    }

    // Un fichero manipulado puede pedir cualquier cantidad de memoria antes de autenticarse.
    @Test
    fun rechazaUnaMemoriaDesorbitada() {
        val pide64GiB = parametros(memoria = 64uL * 1024uL * 1024uL * 1024uL)

        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, pide64GiB) }
    }

    @Test
    fun rechazaIteracionesYParalelismoDesorbitados() {
        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, parametros(iteraciones = 1_000_000uL)) }
        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, parametros(paralelismo = 100_000u)) }
        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, parametros(memoria = ULong.MAX_VALUE)) }
    }

    @Test
    fun rechazaUnaKdfDesconocida() {
        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, parametros(uuid = ByteArray(16))) }
    }
}
