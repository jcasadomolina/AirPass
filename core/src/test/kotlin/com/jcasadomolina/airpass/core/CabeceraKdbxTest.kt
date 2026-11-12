package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.sha256
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CabeceraKdbxTest {

    private val fichero: ByteArray =
        javaClass.getResource("/keepass-aes-aeskdf.kdbx")!!.readBytes()

    // El primer campo empieza en el byte 12: tipo (1 byte), longitud (4 bytes) y valor.
    private val posLongitudPrimerCampo = 13
    private val posValorPrimerCampo = 17

    @Test
    fun leeLosCamposDeUnFicheroDeKeePass() {
        val cabecera = leerCabeceraKdbx(fichero)

        assertEquals(VersionKdbx(mayor = 4, menor = 0), cabecera.version)
        assertEquals(Cifrado.AES256, cabecera.cifrado)
        assertTrue(cabecera.comprimido)
        assertEquals(32, cabecera.semillaMaestra.size)
        assertEquals(16, cabecera.iv.size)
        assertTrue(cabecera.parametrosKdf.isNotEmpty())
    }

    // Tras la cabecera, KDBX 4 guarda el SHA-256 de la propia cabecera.
    // Si coincide, la longitud calculada es exacta.
    @Test
    fun laLongitudAbarcaExactamenteLaCabecera() {
        val cabecera = leerCabeceraKdbx(fichero)
        val fin = cabecera.longitud

        assertContentEquals(
            fichero.copyOfRange(fin, fin + 32),
            sha256(fichero.copyOf(fin)),
        )
    }

    @Test
    fun rechazaUnaLongitudMayorQueElFichero() {
        val manipulado = fichero.copyOf()
        manipulado[posLongitudPrimerCampo + 3] = 0x7F // unos 2 GB

        assertFailsWith<IllegalArgumentException> { leerCabeceraKdbx(manipulado) }
    }

    @Test
    fun rechazaUnaLongitudNegativa() {
        val manipulado = fichero.copyOf()
        for (i in 0..3) manipulado[posLongitudPrimerCampo + i] = 0xFF.toByte()

        assertFailsWith<IllegalArgumentException> { leerCabeceraKdbx(manipulado) }
    }

    @Test
    fun rechazaUnaCabeceraSinCampoDeFin() {
        val truncado = fichero.copyOf(40)

        assertFailsWith<IllegalArgumentException> { leerCabeceraKdbx(truncado) }
    }

    @Test
    fun rechazaUnCifradoDesconocido() {
        val manipulado = fichero.copyOf()
        manipulado[posValorPrimerCampo] = 0x00

        assertFailsWith<IllegalArgumentException> { leerCabeceraKdbx(manipulado) }
    }

    @Test
    fun rechazaUnaCabeceraSinCamposObligatorios() {
        val campoDeFin = byteArrayOf(0, 0, 0, 0, 0)
        val soloFin = fichero.copyOf(12) + campoDeFin

        assertFailsWith<IllegalArgumentException> { leerCabeceraKdbx(soloFin) }
    }
}
