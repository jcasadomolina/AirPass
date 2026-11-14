package com.jcasadomolina.airpass.core

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditarKdbxTest {

    private val keepass = "keepass-aes-aeskdf.kdbx"
    private val keepassxc = "keepassxc-chacha20-argon2id.kdbx"
    private val contrasena get() = "airpass-test".toCharArray()

    private fun abrir(nombre: String) = abrirKdbx(javaClass.getResource("/$nombre")!!.readBytes(), contrasena)

    // Guarda y vuelve a abrir: lo que se comprueba es lo que de verdad llega al fichero.
    private fun reabrir(base: BaseDeDatos, nombre: String): BaseDeDatos {
        val guardado = guardarKdbx(base)
        // Para abrirlo a mano con KeePass y KeePassXC.
        File("build/edicion").apply { mkdirs() }.resolve(nombre).writeBytes(guardado)
        return abrirKdbx(guardado, contrasena)
    }

    // Cada entrada con la ruta de su grupo.
    private fun entradas(grupo: Grupo, ruta: String = grupo.nombre): List<Pair<String, Entrada>> =
        grupo.entradas.map { ruta to it } + grupo.grupos.flatMap { entradas(it, "$ruta/${it.nombre}") }

    private fun buscar(base: BaseDeDatos, titulo: String) =
        entradas(base.raiz).singleOrNull { it.second.campos["Title"] == titulo }

    private fun Entrada.contrasena() = protegidos.getValue("Password").toString(Charsets.UTF_8)

    @Test
    fun anadeUnaEntrada() {
        for (nombre in listOf(keepass, keepassxc)) {
            val base = abrir(nombre)
            base.anadirEntrada(base.raiz, "Correo", "ana@example.com", "clave-nueva".toCharArray())

            val (grupo, entrada) = assertNotNull(buscar(reabrir(base, "anadir-$nombre"), "Correo"), nombre)
            assertEquals(base.raiz.nombre, grupo)
            assertEquals("ana@example.com", entrada.campos["UserName"])
            assertEquals("clave-nueva", entrada.contrasena())
            assertEquals("secreto123", buscar(base, "Entrada de prueba")!!.second.contrasena())
        }
    }

    @Test
    fun modificaUnaEntradaYConservaLaVersionAnteriorEnElHistorial() {
        for (nombre in listOf(keepass, keepassxc)) {
            val base = abrir(nombre)
            val original = buscar(base, "Entrada de prueba")!!.second
            val versionesAntes = original.elemento.hijo("History")?.hijos("Entry")?.size ?: 0

            base.modificarEntrada(original, "Entrada cambiada", "otro", "secreto456".toCharArray())

            val reabierta = reabrir(base, "modificar-$nombre")
            assertNull(buscar(reabierta, "Entrada de prueba"), nombre)
            val entrada = buscar(reabierta, "Entrada cambiada")!!.second
            assertEquals("otro", entrada.campos["UserName"])
            assertEquals("secreto456", entrada.contrasena())

            val versiones = entrada.elemento.hijo("History")!!.hijos("Entry")
            assertEquals(versionesAntes + 1, versiones.size, nombre)
            val anterior = versiones.last()
            assertNull(anterior.hijo("History"), "una versión del historial no lleva historial propio")
            val secreto = reabierta.protegidos.getValue(anterior.protegidosDentro().single())
            assertEquals("secreto123", secreto.toString(Charsets.UTF_8), nombre)
        }
    }

    @Test
    fun leeLasFechasYActualizaLaDeModificacion() {
        for (nombre in listOf(keepass, keepassxc)) {
            val base = abrir(nombre)
            val entrada = buscar(base, "Entrada de prueba")!!.second
            val creada = assertNotNull(entrada.creada, nombre)
            val modificada = assertNotNull(entrada.modificada, nombre)
            assertTrue(creada <= modificada, nombre)

            base.modificarEntrada(entrada, "Otra", "usuario", "otra".toCharArray())

            val nueva = buscar(reabrir(base, "fechas-$nombre"), "Otra")!!.second
            assertEquals(creada, nueva.creada, nombre)
            assertTrue(nueva.modificada!! > modificada, nombre)
        }
    }

    @Test
    fun leeFechasEnLosDosFormatosYRechazaLasQueNoEntiende() {
        // 63 713 433 600 s desde el año 1 = 2020-01-01, en Int64 little-endian y Base64.
        assertEquals(Instant.parse("2020-01-01T00:00:00Z"), leerFecha("ANid1Q4AAAA="))
        assertEquals(Instant.parse("2020-01-01T00:00:00Z"), leerFecha("2020-01-01T00:00:00Z"))
        assertNull(leerFecha("no es una fecha"))
        assertNull(leerFecha(null))
    }

    // El fichero de KeePass tiene papelera: la entrada se mueve a ella.
    @Test
    fun borrarMueveLaEntradaALaPapelera() {
        val base = abrir(keepass)
        val (grupoAntes, entrada) = buscar(base, "Entrada de prueba")!!

        base.borrarEntrada(entrada)

        val (grupoDespues, movida) = assertNotNull(buscar(reabrir(base, "papelera-$keepass"), "Entrada de prueba"))
        assertTrue(grupoDespues != grupoAntes, "sigue en $grupoDespues")
        assertEquals("secreto123", movida.contrasena())
    }

    // El de KeePassXC todavía no tiene papelera: la entrada se elimina y queda registrado.
    @Test
    fun borrarSinPapeleraEliminaLaEntradaYLoRegistra() {
        val base = abrir(keepassxc)
        val entrada = buscar(base, "Entrada de prueba")!!.second
        val uuid = entrada.elemento.hijo("UUID")!!.textContent

        base.borrarEntrada(entrada)

        val reabierta = reabrir(base, "borrar-$keepassxc")
        assertNull(buscar(reabierta, "Entrada de prueba"))
        val borrados = reabierta.documento.documentElement.hijo("Root")!!.hijo("DeletedObjects")!!.hijos("DeletedObject")
        assertTrue(borrados.any { it.hijo("UUID")?.textContent == uuid })
    }

    @Test
    fun guardarEnFicheroSustituyeElOriginalSinDejarTemporales() {
        val carpeta = Files.createTempDirectory("airpass-test")
        try {
            val destino = carpeta.resolve("base.kdbx")
            Files.write(destino, javaClass.getResource("/$keepass")!!.readBytes())
            val base = abrirKdbx(Files.readAllBytes(destino), contrasena)
            base.anadirEntrada(base.raiz, "Nueva", "usuario", "clave".toCharArray())

            guardarEnFichero(base, destino)

            assertEquals(listOf("base.kdbx"), carpeta.toFile().list()!!.toList())
            assertNotNull(buscar(abrirKdbx(Files.readAllBytes(destino), contrasena), "Nueva"))
        } finally {
            carpeta.toFile().deleteRecursively()
        }
    }

    // Tras bloquear, la clave ya no está: guardar no debe escribir un fichero con una clave a ceros.
    @Test
    fun noSeGuardaUnaBaseConLosSecretosBorrados() {
        val carpeta = Files.createTempDirectory("airpass-test")
        try {
            val destino = carpeta.resolve("base.kdbx")
            val original = javaClass.getResource("/$keepass")!!.readBytes()
            Files.write(destino, original)
            val base = abrirKdbx(original, contrasena)
            base.borrarSecretos()

            assertFailsWith<IllegalStateException> { guardarEnFichero(base, destino) }
            assertContentEquals(original, Files.readAllBytes(destino))
        } finally {
            carpeta.toFile().deleteRecursively()
        }
    }
}
