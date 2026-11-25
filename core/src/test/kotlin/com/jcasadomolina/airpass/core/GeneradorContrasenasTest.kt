package com.jcasadomolina.airpass.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeneradorContrasenasTest {

    @Test
    fun porDefectoUsaMayusculasMinusculasDigitosYEspecialesConUnoDeCada() {
        val opciones = OpcionesGenerador()
        repeat(200) {
            val contrasena = generarContrasena(opciones)
            assertEquals(16, contrasena.size)
            for (grupo in listOf(Caracteres.MAYUSCULAS, Caracteres.MINUSCULAS, Caracteres.DIGITOS, Caracteres.ESPECIAL)) {
                assertTrue(contrasena.any { it in grupo.juego }, "${String(contrasena)} sin ${grupo.name}")
            }
        }
    }

    @Test
    fun excluyeLosCaracteresParecidos() {
        val opciones = OpcionesGenerador(longitud = 200, caracteres = setOf(Caracteres.MAYUSCULAS, Caracteres.DIGITOS), excluirParecidos = true)
        val contrasena = generarContrasena(opciones)
        assertTrue(contrasena.none { it in PARECIDOS })
    }

    @Test
    fun calculaLosBitsDeEntropia() {
        val opciones = OpcionesGenerador(longitud = 10, caracteres = setOf(Caracteres.DIGITOS))
        assertTrue(abs(opciones.bits() - 10 * 3.3219) < 0.001)
        assertEquals(0.0, opciones.copy(caracteres = emptySet()).bits())
        assertEquals(94, Caracteres.EXTENDIDO.juego.length)
    }

    @Test
    fun estimaLaFuerzaDeUnaContrasenaEscrita() {
        assertEquals(0.0, estimarBits(""))
        assertTrue(abs(estimarBits("123456") - 6 * 3.3219) < 0.001)
        assertTrue(abs(estimarBits("abcDEF12") - 8 * kotlin.math.log2(62.0)) < 0.001)
        assertTrue(estimarBits("contraseña") > estimarBits("contrasena"))
    }

    @Test
    fun explicaPorQueNoSePuedeGenerar() {
        assertNull(OpcionesGenerador().problema())
        assertNotNull(OpcionesGenerador(caracteres = emptySet()).problema())
        assertNotNull(OpcionesGenerador(longitud = 0).problema())
        assertNotNull(OpcionesGenerador(longitud = 3).problema())
        assertNull(OpcionesGenerador(longitud = 3, unoDeCada = false).problema())
        assertFailsWith<IllegalArgumentException> { generarContrasena(OpcionesGenerador(longitud = 3)) }
    }
}
