package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.FlujoChaCha20
import com.jcasadomolina.airpass.crypto.sha512
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XmlKdbxTest {

    private fun todasLasEntradas(grupo: Grupo): List<Entrada> =
        grupo.entradas + grupo.grupos.flatMap { todasLasEntradas(it) }

    private fun contenidoCon(xml: String, claveInterna: ByteArray = ByteArray(64), cifradoInterno: Int = 3) =
        ContenidoKdbx(cifradoInterno, claveInterna, xml.toByteArray())

    @Test
    fun leeLaEntradaDeUnFicheroDeKeePass() {
        val fichero = javaClass.getResource("/keepass-aes-aeskdf.kdbx")!!.readBytes()
        val cabecera = leerCabeceraKdbx(fichero)
        val claves = derivarClaves(fichero, cabecera, "airpass-test".toCharArray())

        val raiz = leerXmlKdbx(descifrarKdbx(fichero, cabecera, claves))
        val entrada = todasLasEntradas(raiz).single { it.campos["Title"] == "Entrada de prueba" }

        assertEquals("usuario", entrada.campos["UserName"])
        assertEquals("secreto123", entrada.protegidos.getValue("Password").toString(Charsets.UTF_8))
    }

    // El historial va en el XML antes que la entrada siguiente. Si no se descifrara,
    // el flujo quedaría desplazado y la segunda contraseña saldría mal.
    @Test
    fun elHistorialAvanzaElFlujoDelCifradoInterno() {
        val claveInterna = ByteArray(64) { it.toByte() }
        val material = sha512(claveInterna)
        val flujo = FlujoChaCha20(material.copyOfRange(0, 32), material.copyOfRange(32, 44))
        fun protegido(texto: String) = Base64.getEncoder().encodeToString(flujo.aplicar(texto.toByteArray()))
        fun entrada(contrasena: String, extra: String = "") =
            """<Entry><String><Key>Password</Key><Value Protected="True">${protegido(contrasena)}</Value></String>$extra</Entry>"""

        val primera = protegido("uno")
        val antigua = entrada("antigua")
        val segunda = entrada("dos")
        val xml = """<KeePassFile><Root><Group><Name>Raíz</Name>
            <Entry><String><Key>Password</Key><Value Protected="True">$primera</Value></String>
            <History>$antigua</History></Entry>
            $segunda
            </Group></Root></KeePassFile>"""

        val raiz = leerXmlKdbx(contenidoCon(xml, claveInterna))

        assertEquals("Raíz", raiz.nombre)
        assertEquals(
            listOf("uno", "dos"),
            raiz.entradas.map { it.protegidos.getValue("Password").toString(Charsets.UTF_8) },
        )
    }

    @Test
    fun rechazaUnXmlConDtd() {
        val xml = """<!DOCTYPE x [<!ENTITY secreto SYSTEM "file:///C:/Windows/win.ini">]>
            <KeePassFile><Root><Group><Name>&secreto;</Name></Group></Root></KeePassFile>"""

        assertFailsWith<IllegalArgumentException> { leerXmlKdbx(contenidoCon(xml)) }
    }

    @Test
    fun rechazaUnXmlMalFormado() {
        assertFailsWith<IllegalArgumentException> { leerXmlKdbx(contenidoCon("<KeePassFile><Root>")) }
    }

    @Test
    fun rechazaUnXmlSinGrupoRaiz() {
        assertFailsWith<IllegalArgumentException> { leerXmlKdbx(contenidoCon("<KeePassFile/>")) }
    }

    @Test
    fun rechazaUnCifradoInternoDesconocido() {
        val xml = "<KeePassFile><Root><Group/></Root></KeePassFile>"

        assertFailsWith<IllegalArgumentException> { leerXmlKdbx(contenidoCon(xml, cifradoInterno = 2)) }
    }
}
