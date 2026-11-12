package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ClavesKdbxTest {

    private val fichero: ByteArray =
        javaClass.getResource("/keepass-aes-aeskdf.kdbx")!!.readBytes()
    private val cabecera = leerCabeceraKdbx(fichero)

    private fun derivar(datos: ByteArray, contrasena: String) =
        derivarClaves(datos, leerCabeceraKdbx(datos), contrasena.toCharArray())

    @Test
    fun leeLosParametrosDeAesKdf() {
        val parametros = leerDiccionarioVariante(cabecera.parametrosKdf)

        assertEquals("c9d9f39a628a4460bf740d08c18a4fea", (parametros["\$UUID"] as ByteArray).toHexString())
        assertEquals(32, (parametros["S"] as ByteArray).size)
        assertTrue(parametros["R"] as Long > 0)
    }

    @Test
    fun rechazaUnDiccionarioTruncado() {
        val truncado = cabecera.parametrosKdf.copyOf(10)

        assertFailsWith<IllegalArgumentException> { leerDiccionarioVariante(truncado) }
    }

    @Test
    fun laContrasenaCorrectaDerivaLasClaves() {
        val claves = derivar(fichero, "airpass-test")

        assertEquals(32, claves.cifrado.size)
        assertEquals(64, claves.hmac.size)
    }

    @Test
    fun rechazaUnaContrasenaIncorrecta() {
        assertFailsWith<ClaveIncorrectaException> { derivar(fichero, "airpass-tesT") }
    }

    @Test
    fun rechazaUnaCabeceraDanada() {
        val danado = fichero.copyOf()
        danado[POS_COMPRESION] = 0

        assertFailsWith<IllegalArgumentException> { derivar(danado, "airpass-test") }
    }

    // Un atacante puede cambiar la cabecera y recalcular su SHA-256, que no usa
    // ninguna clave. El HMAC sí la usa, y por eso detecta el cambio.
    @Test
    fun detectaUnaCabeceraManipuladaConElHashRecalculado() {
        val manipulado = fichero.copyOf()
        manipulado[POS_COMPRESION] = 0
        val fin = cabecera.longitud
        sha256(manipulado.copyOf(fin)).copyInto(manipulado, fin)

        assertFailsWith<ClaveIncorrectaException> { derivar(manipulado, "airpass-test") }
    }

    @Test
    fun rechazaUnFicheroQueTerminaTrasLaCabecera() {
        val truncado = fichero.copyOf(cabecera.longitud + 10)

        assertFailsWith<IllegalArgumentException> { derivarClaves(truncado, cabecera, "airpass-test".toCharArray()) }
    }

    private companion object {
        // Valor del campo de compresión en el fichero de prueba: 1 = GZip, 0 = sin comprimir.
        const val POS_COMPRESION = 0x26
    }
}
