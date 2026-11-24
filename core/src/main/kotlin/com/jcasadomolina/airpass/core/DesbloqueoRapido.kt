package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.bytesAleatorios
import com.jcasadomolina.airpass.crypto.sha256
import java.security.MessageDigest

/**
 * Desbloqueo rápido: tras abrir la base con la contraseña completa, se puede volver a ella escribiendo solo sus
 * últimos caracteres. Solo guarda en memoria un resumen con sal de esos caracteres, nunca los caracteres. Unos pocos
 * caracteres se adivinan rápido, así que la protección la dan las reglas: un único intento, caducidad y nada en disco.
 */
class DesbloqueoRapido private constructor(val caracteres: Int, private val sal: ByteArray, private var resumen: ByteArray?) {
    private var bloqueadaEn = 0L

    /** Empieza a contar la caducidad. Se llama cada vez que se bloquea la base. */
    fun bloquear(ahora: Long = System.nanoTime()) {
        bloqueadaEn = ahora
    }

    /** Nanosegundos que faltan para que caduque desde el último bloqueo; 0 si ya caducó o se olvidó. */
    fun restante(ahora: Long = System.nanoTime()): Long =
        if (resumen == null) 0 else (CADUCIDAD_NANOS - (ahora - bloqueadaEn)).coerceAtLeast(0)

    /**
     * Comprueba el código. Un fallo, o un intento después de caducar, lo olvida para siempre: no hay segundo intento
     * y habrá que usar la contraseña completa. Un acierto lo conserva para el próximo bloqueo.
     */
    fun intentar(intento: CharArray, ahora: Long = System.nanoTime()): Boolean {
        val guardado = resumen ?: return false
        val acierto = restante(ahora) > 0 && MessageDigest.isEqual(guardado, resumir(sal, intento))
        if (!acierto) olvidar()
        return acierto
    }

    /** Borra el resumen de la memoria: el desbloqueo rápido deja de funcionar. */
    fun olvidar() {
        resumen?.fill(0)
        resumen = null
    }

    companion object {
        const val CADUCIDAD_MINUTOS = 15
        private const val CADUCIDAD_NANOS = CADUCIDAD_MINUTOS * 60_000_000_000L

        /**
         * Prepara el desbloqueo rápido con los últimos `caracteres` de la contraseña maestra. Devuelve null si la
         * contraseña no es más larga que eso, porque el código sería la contraseña entera.
         */
        fun desde(contrasena: CharArray, caracteres: Int): DesbloqueoRapido? {
            require(caracteres in 1..16) { "Número de caracteres no válido: $caracteres" }
            if (contrasena.size <= caracteres) return null
            val ultimos = contrasena.copyOfRange(contrasena.size - caracteres, contrasena.size)
            val sal = bytesAleatorios(16)
            return DesbloqueoRapido(caracteres, sal, resumir(sal, ultimos)).also { ultimos.fill('\u0000') }
        }

        /** SHA-256 de la sal seguida de los caracteres en UTF-8; los bytes intermedios se borran. */
        private fun resumir(sal: ByteArray, caracteres: CharArray): ByteArray {
            val utf8 = aUtf8(caracteres)
            val datos = sal + utf8
            return sha256(datos).also {
                utf8.fill(0)
                datos.fill(0)
            }
        }
    }
}
