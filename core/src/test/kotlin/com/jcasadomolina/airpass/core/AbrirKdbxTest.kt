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
