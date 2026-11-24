package com.jcasadomolina.airpass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesbloqueoRapidoTest {

    private val minuto = 60_000_000_000L

    @Test
    fun aceptaLosUltimosCaracteresYSirveEnCadaBloqueo() {
        val rapido = DesbloqueoRapido.desde("airpass-test".toCharArray(), 3)!!
        assertEquals(3, rapido.caracteres)

        rapido.bloquear(ahora = 0)
        assertTrue(rapido.intentar("est".toCharArray(), ahora = minuto))
        rapido.bloquear(ahora = 10 * minuto)
        assertTrue(rapido.intentar("est".toCharArray(), ahora = 20 * minuto))
    }

    @Test
    fun unFalloLoOlvidaParaSiempre() {
        val rapido = DesbloqueoRapido.desde("airpass-test".toCharArray(), 3)!!
        rapido.bloquear(ahora = 0)
        assertFalse(rapido.intentar("tes".toCharArray(), ahora = 1))
        assertFalse(rapido.intentar("est".toCharArray(), ahora = 2))
        assertEquals(0, rapido.restante(ahora = 3))
    }

    @Test
    fun caducaYDespuesTampocoSirveElCodigoCorrecto() {
        val rapido = DesbloqueoRapido.desde("airpass-test".toCharArray(), 4)!!
        rapido.bloquear(ahora = 0)
        assertEquals(minuto, rapido.restante(ahora = (DesbloqueoRapido.CADUCIDAD_MINUTOS - 1) * minuto))
        assertFalse(rapido.intentar("test".toCharArray(), ahora = DesbloqueoRapido.CADUCIDAD_MINUTOS * minuto))
        assertFalse(rapido.intentar("test".toCharArray(), ahora = 1))
    }

    @Test
    fun noSePuedeSiElCodigoSeriaLaContrasenaEntera() {
        assertNull(DesbloqueoRapido.desde("abc".toCharArray(), 3))
        assertNull(DesbloqueoRapido.desde("ab".toCharArray(), 3))
    }
}
