package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.TipoArgon2
import com.jcasadomolina.airpass.crypto.argon2
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Argon2KdbxTest {

    private val argon2d = "ef636ddf8c29444b91f7a9a403e30a0c".hexToByteArray()
    private val argon2id = "9e298b1956db4773b23dfc3ec6f0a1e6".hexToByteArray()
    private val compuesta = ByteArray(32) { 5 }
    private val sal = ByteArray(32) { 6 }

    // Construye un diccionario de parámetros como los que escribe KeePass.
    private fun diccionario(vararg valores: Pair<String, Any>): ByteArray {
        fun entero(bytes: Int, valor: Long): ByteArray {
            val buffer = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bytes == 4) buffer.putInt(valor.toInt()) else buffer.putLong(valor)
            return buffer.array()
        }
        val salida = ByteArrayOutputStream()
        salida.write(byteArrayOf(0x00, 0x01)) // versión 1.0
        for ((nombre, valor) in valores) {
            val (tipo, bytes) = when (valor) {
                is Int -> 0x04 to entero(4, valor.toLong())
                is Long -> 0x05 to entero(8, valor)
                else -> 0x42 to valor as ByteArray
            }
            salida.write(tipo)
            salida.write(entero(4, nombre.toByteArray().size.toLong()))
            salida.write(nombre.toByteArray())
            salida.write(entero(4, bytes.size.toLong()))
            salida.write(bytes)
        }
        salida.write(0)
        return salida.toByteArray()
    }

    private fun parametros(
        uuid: ByteArray = argon2id,
        memoria: Long = 64 * 1024,
        iteraciones: Long = 2,
        paralelismo: Int = 2,
    ) = diccionario("\$UUID" to uuid, "S" to sal, "M" to memoria, "I" to iteraciones, "P" to paralelismo, "V" to 0x13)

    @Test
    fun usaArgon2idConLosParametrosDelFichero() {
        assertEquals(
            argon2(TipoArgon2.ID, compuesta, sal, iteraciones = 2, memoriaKiB = 64, paralelismo = 2).toHexString(),
            transformarClave(compuesta, parametros()).toHexString(),
        )
    }

    @Test
    fun distingueArgon2dDeArgon2id() {
        assertEquals(
            argon2(TipoArgon2.D, compuesta, sal, iteraciones = 2, memoriaKiB = 64, paralelismo = 2).toHexString(),
            transformarClave(compuesta, parametros(uuid = argon2d)).toHexString(),
        )
    }

    // Un fichero manipulado puede pedir cualquier cantidad de memoria antes de autenticarse.
    @Test
    fun rechazaUnaMemoriaDesorbitada() {
        val pide64GiB = parametros(memoria = 64L * 1024 * 1024 * 1024)

        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, pide64GiB) }
    }

    @Test
    fun rechazaIteracionesYParalelismoDesorbitados() {
        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, parametros(iteraciones = 1_000_000)) }
        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, parametros(paralelismo = 100_000)) }
        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, parametros(memoria = -1)) }
    }

    @Test
    fun rechazaUnaKdfDesconocida() {
        assertFailsWith<IllegalArgumentException> { transformarClave(compuesta, parametros(uuid = ByteArray(16))) }
    }
}
