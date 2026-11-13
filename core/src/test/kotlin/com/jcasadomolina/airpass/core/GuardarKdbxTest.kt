package com.jcasadomolina.airpass.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.w3c.dom.Element

class GuardarKdbxTest {

    private val corpus = listOf(
        "keepass-aes-aeskdf.kdbx",
        "keepassxc-aes-argon2d.kdbx",
        "keepassxc-aes-argon2id.kdbx",
        "keepassxc-chacha20-argon2id.kdbx",
    )
    private val contrasena get() = "airpass-test".toCharArray()

    private fun fichero(nombre: String) = javaClass.getResource("/$nombre")!!.readBytes()

    // Todo lo que contiene el documento, con los valores protegidos ya descifrados:
    // el nombre de cada elemento, su texto si no tiene hijos y el secreto si es protegido.
    private fun contenido(base: BaseDeDatos): List<String> {
        val elementos = base.documento.getElementsByTagName("*")
        return (0 until elementos.length).map { i ->
            val elemento = elementos.item(i) as Element
            val secreto = base.protegidos[elemento]
            when {
                secreto != null -> "${elemento.tagName}=[protegido]${secreto.toString(Charsets.UTF_8)}"
                elemento.getElementsByTagName("*").length == 0 -> "${elemento.tagName}=${elemento.textContent}"
                else -> elemento.tagName
            }
        }
    }

    // Abrir, guardar y volver a abrir no pierde ni cambia nada, tampoco lo que AirPass no entiende.
    @Test
    fun laIdaYVueltaConservaTodoElContenido() {
        val carpeta = File("build/ida-y-vuelta").apply { mkdirs() }
        for (nombre in corpus) {
            val original = abrirKdbx(fichero(nombre), contrasena)
            val antes = contenido(original)

            val guardado = guardarKdbx(original, contrasena)
            // Para abrirlo a mano con KeePass y KeePassXC.
            File(carpeta, "airpass-$nombre").writeBytes(guardado)

            val reabierto = abrirKdbx(guardado, contrasena)
            assertEquals(antes, contenido(reabierto), nombre)
            assertEquals(original.cabecera.cifrado, reabierto.cabecera.cifrado, nombre)
            assertEquals(original.cabecera.comprimido, reabierto.cabecera.comprimido, nombre)
            assertEquals(original.cabecera.version, reabierto.cabecera.version, nombre)
            assertEquals(original.adjuntos.size, reabierto.adjuntos.size, nombre)
        }
    }

    // Cada guardado renueva todo lo aleatorio: ninguna clave ni vector se usa dos veces.
    @Test
    fun cadaGuardadoRenuevaLaAleatoriedad() {
        val base = abrirKdbx(fichero("keepass-aes-aeskdf.kdbx"), contrasena)
        val original = base.cabecera
        val uno = leerCabeceraKdbx(guardarKdbx(base, contrasena))
        val otro = leerCabeceraKdbx(guardarKdbx(base, contrasena))

        for ((a, b) in listOf(original to uno, uno to otro)) {
            assertFalse(a.semillaMaestra.contentEquals(b.semillaMaestra))
            assertFalse(a.iv.contentEquals(b.iv))
            assertFalse(a.parametrosKdf.contentEquals(b.parametrosKdf))
        }
    }

    @Test
    fun conservaLosParametrosDeLaKdfSalvoLaSal() {
        for (nombre in corpus) {
            val base = abrirKdbx(fichero(nombre), contrasena)
            val antes = leerDiccionarioVariante(base.cabecera.parametrosKdf)
            val despues = leerDiccionarioVariante(leerCabeceraKdbx(guardarKdbx(base, contrasena)).parametrosKdf)

            assertEquals(antes.keys.toList(), despues.keys.toList(), nombre)
            for (clave in antes.keys - "S") {
                val a = antes.getValue(clave)
                val b = despues.getValue(clave)
                if (a is ByteArray) assertContentEquals(a, b as ByteArray, "$nombre $clave") else assertEquals(a, b, "$nombre $clave")
            }
        }
    }

    @Test
    fun elFicheroGuardadoRechazaOtraContrasena() {
        val guardado = guardarKdbx(abrirKdbx(fichero("keepassxc-chacha20-argon2id.kdbx"), contrasena), contrasena)

        assertFailsWith<ClaveIncorrectaException> { abrirKdbx(guardado, "otra".toCharArray()) }
    }

    @Test
    fun seGuardaConUnaContrasenaNueva() {
        val base = abrirKdbx(fichero("keepass-aes-aeskdf.kdbx"), contrasena)
        val antes = contenido(base)
        val guardado = guardarKdbx(base, "nueva".toCharArray())

        assertFailsWith<ClaveIncorrectaException> { abrirKdbx(guardado, contrasena) }
        assertEquals(antes, contenido(abrirKdbx(guardado, "nueva".toCharArray())))
    }

    // El diccionario de la KDF se reescribe byte a byte igual que lo escribieron KeePass y KeePassXC.
    @Test
    fun elDiccionarioSeReescribeIdentico() {
        for (nombre in corpus) {
            val original = leerCabeceraKdbx(fichero(nombre)).parametrosKdf

            assertContentEquals(original, escribirDiccionarioVariante(leerDiccionarioVariante(original)), nombre)
        }
    }

    @Test
    fun losBloquesSeLeenIgualQueSeEscriben() {
        val clave = ByteArray(64) { 3 }
        for (datos in listOf(ByteArray(0), ByteArray(16) { 1 }, ByteArray(100) { it.toByte() })) {
            val bloques = escribirBloquesAutenticados(datos, clave, tamanoBloque = 16)

            assertContentEquals(datos, leerBloquesAutenticados(bloques, 0, clave))
        }
    }

    // Con bloques de 16 bytes, cada uno ocupa 32 (HMAC) + 4 (longitud) + 16 (datos) = 52 bytes.
    @Test
    fun detectaBloquesIntercambiados() {
        val clave = ByteArray(64) { 3 }
        val bloques = escribirBloquesAutenticados(ByteArray(48) { it.toByte() }, clave, tamanoBloque = 16)
        val intercambiados =
            bloques.copyOfRange(52, 104) + bloques.copyOfRange(0, 52) + bloques.copyOfRange(104, bloques.size)

        assertEquals(3 * 52 + 36, bloques.size)
        assertFailsWith<FicheroManipuladoException> { leerBloquesAutenticados(intercambiados, 0, clave) }
    }

    @Test
    fun detectaUnBloqueEliminado() {
        val clave = ByteArray(64) { 3 }
        val bloques = escribirBloquesAutenticados(ByteArray(48) { it.toByte() }, clave, tamanoBloque = 16)
        val sinElSegundo = bloques.copyOfRange(0, 52) + bloques.copyOfRange(104, bloques.size)

        assertFailsWith<FicheroManipuladoException> { leerBloquesAutenticados(sinElSegundo, 0, clave) }
    }
}
