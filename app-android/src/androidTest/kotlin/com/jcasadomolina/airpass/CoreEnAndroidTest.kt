package com.jcasadomolina.airpass

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jcasadomolina.airpass.core.Entrada
import com.jcasadomolina.airpass.core.Grupo
import com.jcasadomolina.airpass.core.abrirKdbx
import com.jcasadomolina.airpass.core.anadirEntrada
import com.jcasadomolina.airpass.core.guardarKdbx
import com.jcasadomolina.airpass.core.guardarVerificado
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreEnAndroidTest {

    private val corpus = listOf(
        "keepass-aes-aeskdf.kdbx",
        "keepassxc-aes-argon2d.kdbx",
        "keepassxc-aes-argon2id.kdbx",
        "keepassxc-chacha20-argon2id.kdbx",
    )

    private fun fichero(nombre: String) =
        InstrumentationRegistry.getInstrumentation().context.assets.open(nombre).use { it.readBytes() }

    private fun entradas(grupo: Grupo): List<Entrada> = grupo.entradas + grupo.grupos.flatMap { entradas(it) }

    private fun contrasenaDe(grupo: Grupo, titulo: String) =
        entradas(grupo).single { it.campos["Title"] == titulo }.protegidos.getValue("Password").toString(Charsets.UTF_8)

    @Test
    fun abreGuardaYReabreTodoElCorpus() {
        for (nombre in corpus) {
            val base = abrirKdbx(fichero(nombre), "airpass-test".toCharArray())
            assertEquals("secreto123", contrasenaDe(base.raiz, "Entrada de prueba"), nombre)

            base.anadirEntrada(base.raiz, "Desde Android", "movil", "clave-movil".toCharArray())
            val reabierta = abrirKdbx(guardarKdbx(base), "airpass-test".toCharArray())

            assertEquals("clave-movil", contrasenaDe(reabierta.raiz, "Desde Android"), nombre)
            assertEquals("secreto123", contrasenaDe(reabierta.raiz, "Entrada de prueba"), nombre)
        }
    }

    // Escribe a través de una Uri, como cuando el fichero llega del selector del sistema.
    @Test
    fun guardaEnUnaUriYSePuedeReabrir() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val destino = File(contexto.cacheDir, "prueba-guardado.kdbx")
        val original = fichero(corpus.first())
        destino.writeBytes(original)
        try {
            val base = abrirKdbx(original, "airpass-test".toCharArray())
            base.anadirEntrada(base.raiz, "Guardada en Android", "movil", "clave-uri".toCharArray())

            escribirEnUri(contexto.contentResolver, Uri.fromFile(destino), guardarVerificado(base))

            val reabierta = abrirKdbx(destino.readBytes(), "airpass-test".toCharArray())
            assertEquals("clave-uri", contrasenaDe(reabierta.raiz, "Guardada en Android"))
        } finally {
            destino.delete()
        }
    }

    @Test
    fun rechazaUnaContrasenaIncorrecta() {
        assertFailsWith<com.jcasadomolina.airpass.core.ClaveIncorrectaException> {
            abrirKdbx(fichero(corpus.last()), "otra".toCharArray())
        }
    }
}
