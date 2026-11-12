package com.jcasadomolina.airpass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContenidoKdbxTest {

    private val fichero: ByteArray =
        javaClass.getResource("/keepass-aes-aeskdf.kdbx")!!.readBytes()
    private val cabecera = leerCabeceraKdbx(fichero)
    private val claves = derivarClaves(fichero, cabecera, "airpass-test".toCharArray())

    // El primer bloque empieza tras la cabecera, su SHA-256 y su HMAC.
    // Cada bloque: HMAC (32 bytes), longitud (4 bytes) y datos.
    private val inicioBloques = cabecera.longitud + 64

    @Test
    fun descifraElContenidoDeUnFicheroDeKeePass() {
        val contenido = descifrarKdbx(fichero, cabecera, claves)
        val xml = contenido.xml.toString(Charsets.UTF_8)

        assertTrue("<KeePassFile>" in xml)
        assertTrue("Entrada de prueba" in xml)
        assertEquals(3, contenido.cifradoInterno) // 3 = ChaCha20
        assertEquals(64, contenido.claveInterna.size)
    }

    // La contraseña de la entrada es un campo protegido: sigue cifrada dentro del XML.
    @Test
    fun laContrasenaDeLaEntradaNoApareceEnClaro() {
        val xml = descifrarKdbx(fichero, cabecera, claves).xml.toString(Charsets.UTF_8)

        assertFalse("secreto123" in xml)
    }

    @Test
    fun detectaUnBloqueManipulado() {
        val manipulado = fichero.copyOf()
        val posDatos = inicioBloques + 36 + 10
        manipulado[posDatos] = (manipulado[posDatos] + 1).toByte()

        assertFailsWith<FicheroManipuladoException> { descifrarKdbx(manipulado, cabecera, claves) }
    }

    @Test
    fun detectaUnaLongitudDeBloqueManipulada() {
        val manipulado = fichero.copyOf()
        val posLongitud = inicioBloques + 32
        manipulado[posLongitud] = (manipulado[posLongitud] - 1).toByte()

        assertFailsWith<FicheroManipuladoException> { descifrarKdbx(manipulado, cabecera, claves) }
    }

    // Quitar el bloque final vacío es la forma de recortar un fichero sin tocar ningún HMAC.
    @Test
    fun detectaQueFaltaElBloqueFinal() {
        val recortado = fichero.copyOf(fichero.size - 36)

        assertFailsWith<IllegalArgumentException> { descifrarKdbx(recortado, cabecera, claves) }
    }

    @Test
    fun rechazaUnFicheroTruncadoEnMitadDeUnBloque() {
        val truncado = fichero.copyOf(fichero.size - 100)

        assertFailsWith<IllegalArgumentException> { descifrarKdbx(truncado, cabecera, claves) }
    }
}
