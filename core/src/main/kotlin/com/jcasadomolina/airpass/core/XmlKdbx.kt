package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.FlujoChaCha20
import com.jcasadomolina.airpass.crypto.sha512
import java.util.Base64
import java.util.IdentityHashMap
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException

// `campos` son los valores normales (título, usuario, URL...). `protegidos` son los
// secretos, ya descifrados y en UTF-8: se guardan como bytes para poder borrarlos.
class Entrada(val campos: Map<String, String>, val protegidos: Map<String, ByteArray>)

class Grupo(val nombre: String, val grupos: List<Grupo>, val entradas: List<Entrada>)

// Una base de datos abierta. `raiz` es lo que AirPass entiende y muestra. El resto es
// todo lo leído del fichero, que se conserva para volver a escribirlo sin perder nada.
class BaseDeDatos internal constructor(
    val raiz: Grupo,
    internal val cabecera: CabeceraKdbx,
    internal val adjuntos: List<ByteArray>,
    internal val documento: Document,
    // Todos los valores protegidos del documento, descifrados, incluidos los del historial.
    internal val protegidos: IdentityHashMap<Element, ByteArray>,
) {
    // Rellena con ceros todos los secretos. Después la base de datos ya no se puede guardar.
    fun borrarSecretos() {
        protegidos.values.forEach { it.fill(0) }
    }
}

internal class XmlLeido(
    val raiz: Grupo,
    val documento: Document,
    val protegidos: IdentityHashMap<Element, ByteArray>,
)

internal const val CHACHA20_INTERNO = 3

// El cifrado interno usa SHA-512 de su clave: 32 bytes de clave y 12 de nonce.
internal fun flujoInterno(claveInterna: ByteArray): FlujoChaCha20 {
    val material = sha512(claveInterna)
    val flujo = FlujoChaCha20(clave = material.copyOfRange(0, 32), nonce = material.copyOfRange(32, 44))
    material.fill(0)
    return flujo
}

// Elementos marcados como protegidos, en el orden del documento.
internal fun Document.elementosProtegidos(): List<Element> {
    val todos = getElementsByTagName("*")
    val protegidos = ArrayList<Element>()
    for (i in 0 until todos.length) {
        val elemento = todos.item(i) as Element
        if (elemento.getAttribute("Protected").equals("True", ignoreCase = true)) protegidos.add(elemento)
    }
    return protegidos
}

private fun Element.hijos(nombre: String): List<Element> {
    val encontrados = ArrayList<Element>()
    for (i in 0 until childNodes.length) {
        val nodo = childNodes.item(i)
        if (nodo is Element && nodo.tagName == nombre) encontrados.add(nodo)
    }
    return encontrados
}

internal fun leerXmlKdbx(contenido: ContenidoKdbx): XmlLeido {
    // shortcut: solo ChaCha20; Salsa20 (2) lo usan los ficheros KDBX 3, que no se leen.
    require(contenido.cifradoInterno == CHACHA20_INTERNO) {
        "Cifrado interno no compatible: ${contenido.cifradoInterno}"
    }

    val fabrica = DocumentBuilderFactory.newInstance()
    // Sin DTD no hay entidades externas: un XML no puede hacer leer ficheros del disco (XXE).
    fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    val analizador = fabrica.newDocumentBuilder()
    analizador.setErrorHandler(null)
    val documento = try {
        analizador.parse(contenido.xml.inputStream())
    } catch (e: SAXException) {
        throw IllegalArgumentException("El XML del fichero no es válido", e)
    }

    // El cifrado interno es un único flujo para todo el documento: los valores protegidos
    // se descifran en el orden en que aparecen, incluidos los del historial.
    val flujo = flujoInterno(contenido.claveInterna)
    val descifrados = IdentityHashMap<Element, ByteArray>()
    for (elemento in documento.elementosProtegidos()) {
        descifrados[elemento] = flujo.aplicar(Base64.getDecoder().decode(elemento.textContent))
    }

    fun entrada(elemento: Element): Entrada {
        val campos = HashMap<String, String>()
        val protegidos = HashMap<String, ByteArray>()
        for (cadena in elemento.hijos("String")) {
            val clave = cadena.hijos("Key").firstOrNull()?.textContent ?: continue
            val valor = cadena.hijos("Value").firstOrNull() ?: continue
            val descifrado = descifrados[valor]
            if (descifrado != null) protegidos[clave] = descifrado else campos[clave] = valor.textContent
        }
        return Entrada(campos, protegidos)
    }

    fun grupo(elemento: Element): Grupo = Grupo(
        nombre = elemento.hijos("Name").firstOrNull()?.textContent.orEmpty(),
        grupos = elemento.hijos("Group").map { grupo(it) },
        entradas = elemento.hijos("Entry").map { entrada(it) },
    )

    val raiz = documento.documentElement.hijos("Root").firstOrNull()?.hijos("Group")?.firstOrNull()
    return XmlLeido(
        raiz = grupo(requireNotNull(raiz) { "El XML no contiene el grupo raíz" }),
        documento = documento,
        protegidos = descifrados,
    )
}

// Abre una base de datos completa. Las claves y el XML descifrado se borran al terminar,
// también si algo falla; solo sobreviven los valores protegidos de la base devuelta.
fun abrirKdbx(datos: ByteArray, contrasena: CharArray): BaseDeDatos {
    val cabecera = leerCabeceraKdbx(datos)
    val claves = derivarClaves(datos, cabecera, contrasena)
    try {
        val contenido = descifrarKdbx(datos, cabecera, claves)
        try {
            val xml = leerXmlKdbx(contenido)
            return BaseDeDatos(xml.raiz, cabecera, contenido.adjuntos, xml.documento, xml.protegidos)
        } finally {
            contenido.claveInterna.fill(0)
            contenido.xml.fill(0)
        }
    } finally {
        claves.cifrado.fill(0)
        claves.hmac.fill(0)
    }
}
