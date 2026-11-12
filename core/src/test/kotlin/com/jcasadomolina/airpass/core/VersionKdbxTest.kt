package com.jcasadomolina.airpass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VersionKdbxTest {

    private val fichero: ByteArray =
        javaClass.getResource("/keepass-aes-aeskdf.kdbx")!!.readBytes()

    @Test
    fun leeLaVersionDeUnFicheroDeKeePass() {
        assertEquals(VersionKdbx(mayor = 4, menor = 0), leerVersionKdbx(fichero))
    }

    @Test
    fun rechazaUnaFirmaIncorrecta() {
        val manipulado = fichero.copyOf()
        manipulado[0] = 0x00

        assertFailsWith<IllegalArgumentException> { leerVersionKdbx(manipulado) }
    }

    @Test
    fun rechazaUnFicheroTruncado() {
        val truncado = fichero.copyOf(11)

        assertFailsWith<IllegalArgumentException> { leerVersionKdbx(truncado) }
    }

    @Test
    fun rechazaUnaVersionMayorDistintaDe4() {
        val kdbx3 = fichero.copyOf()
        kdbx3[10] = 0x03

        assertFailsWith<IllegalArgumentException> { leerVersionKdbx(kdbx3) }
    }
}
