package com.jcasadomolina.airpass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AbrirKdbxTest {

    private val fichero: ByteArray =
        javaClass.getResource("/keepass-aes-aeskdf.kdbx")!!.readBytes()

    private fun todasLasEntradas(grupo: Grupo): List<Entrada> =
        grupo.entradas + grupo.grupos.flatMap { todasLasEntradas(it) }

    @Test
    fun abreElFicheroConLaContrasenaCorrecta() {
        val raiz = abrirKdbx(fichero, "airpass-test".toCharArray())

        assertTrue(todasLasEntradas(raiz).any { it.campos["Title"] == "Entrada de prueba" })
    }

    // El corpus: cada fichero combina un programa, un cifrado y una KDF distintos.
    @Test
    fun abreTodosLosFicherosDelCorpus() {
        val corpus = listOf(
            "keepass-aes-aeskdf.kdbx",
            "keepassxc-aes-argon2d.kdbx",
            "keepassxc-aes-argon2id.kdbx",
            "keepassxc-chacha20-argon2id.kdbx",
        )
        for (nombre in corpus) {
            val datos = javaClass.getResource("/$nombre")!!.readBytes()
            val raiz = abrirKdbx(datos, "airpass-test".toCharArray())
            val entrada = todasLasEntradas(raiz).singleOrNull { it.campos["Title"] == "Entrada de prueba" }

            assertEquals("usuario", entrada?.campos?.get("UserName"), nombre)
            assertEquals("secreto123", entrada?.protegidos?.get("Password")?.toString(Charsets.UTF_8), nombre)
        }
    }

    @Test
    fun rechazaUnaContrasenaIncorrectaEnTodoElCorpus() {
        for (nombre in listOf("keepassxc-aes-argon2d.kdbx", "keepassxc-aes-argon2id.kdbx", "keepassxc-chacha20-argon2id.kdbx")) {
            val datos = javaClass.getResource("/$nombre")!!.readBytes()

            assertFailsWith<ClaveIncorrectaException>(nombre) { abrirKdbx(datos, "otra".toCharArray()) }
        }
    }

    @Test
    fun rechazaUnaContrasenaIncorrecta() {
        assertFailsWith<ClaveIncorrectaException> { abrirKdbx(fichero, "otra".toCharArray()) }
    }

    @Test
    fun borrarSecretosDejaLasContrasenasACero() {
        val raiz = abrirKdbx(fichero, "airpass-test".toCharArray())
        val entrada = todasLasEntradas(raiz).single { it.campos["Title"] == "Entrada de prueba" }

        raiz.borrarSecretos()

        assertEquals(List(10) { 0.toByte() }, entrada.protegidos.getValue("Password").toList())
    }
}
