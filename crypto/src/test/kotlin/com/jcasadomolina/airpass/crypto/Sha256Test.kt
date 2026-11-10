package com.jcasadomolina.airpass.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    // Vector de prueba del NIST (FIPS 180-4) para la entrada "abc".
    @Test
    fun vectorAbc() {
        val hash = sha256("abc".toByteArray())

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hash.toHexString(),
        )
    }
}
