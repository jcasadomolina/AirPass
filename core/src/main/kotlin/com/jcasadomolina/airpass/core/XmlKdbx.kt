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
// `elemento` es su sitio en el documento, que es lo que se modifica al editar.
class Entrada internal constructor(
    val campos: Map<String, String>,
    val protegidos: Map<String, ByteArray>,
    internal val elemento: Element,
)

class Grupo internal constructor(
    val nombre: String,
    val grupos: List<Grupo>,
    val entradas: List<Entrada>,
    internal val elemento: Element,
)

// Una base de datos abierta. `raiz` es lo que AirPass entiende y muestra. El resto es
// todo lo leído del fichero, que se conserva para volver a escribirlo sin perder nada.
class BaseDeDatos internal constructor(
    internal val cabecera: CabeceraKdbx,
    internal val adjuntos: List<ByteArray>,
    internal val documento: Document,
    // Todos los valores protegidos del documento, descifrados, incluidos los del historial.
    internal val protegidos: IdentityHashMap<Element, ByteArray>,
    // Permite guardar sin volver a pedir la contraseña. De ella no se puede obtener la contraseña.
    internal val claveCompuesta: ByteArray,
) {
    var raiz: Grupo = construirArbol(documento, protegidos)
        private set

    // Pasa a false al borrar los secretos. Una base bloqueada no se puede guardar.
    var abierta: Boolean = true
        private set

    // Vuelve a leer el árbol del documento. Se llama después de cada cambio.
    internal fun refrescar() {
        raiz = construirArbol(documento, protegidos)
    }

    // Rellena con ceros todos los secretos. Después la base de datos ya no se puede guardar.
    fun borrarSecretos() {
        abierta = false
        protegidos.values.forEach { it.fill(0) }
        claveCompuesta.fill(0)
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

internal fun Element.esProtegido(): Boolean = getAttribute("Protected").equals("True", ignoreCase = true)

// Elementos marcados como protegidos dentro de este, en el orden del documento.
internal fun Element.protegidosDentro(): List<Element> {
    val todos = getElementsByTagName("*")
    return (0 until todos.length).map { todos.item(it) as Element }.filter { it.esProtegido() }
}

internal fun Element.hijos(nombre: String): List<Element> {
    val encontrados = ArrayList<Element>()
    for (i in 0 until childNodes.length) {
        val nodo = childNodes.item(i)
        if (nodo is Element && nodo.tagName == nombre) encontrados.add(nodo)
    }
    return encontrados
}

internal fun Element.hijo(nombre: String): Element? = hijos(nombre).firstOrNull()

internal fun construirArbol(documento: Document, protegidos: Map<Element, ByteArray>): Grupo {
    fun entrada(elemento: Element): Entrada {
        val campos = HashMap<String, String>()
        val secretos = HashMap<String, ByteArray>()
        for (cadena in elemento.hijos("String")) {
            val clave = cadena.hijo("Key")?.textContent ?: continue
            val valor = cadena.hijo("Value") ?: continue
            val descifrado = protegidos[valor]
            if (descifrado != null) secretos[clave] = descifrado else campos[clave] = valor.textContent
        }
        return Entrada(campos, secretos, elemento)
    }

    fun grupo(elemento: Element): Grupo = Grupo(
        nombre = elemento.hijo("Name")?.textContent.orEmpty(),
        grupos = elemento.hijos("Group").map { grupo(it) },
        entradas = elemento.hijos("Entry").map { entrada(it) },
        elemento = elemento,
    )

    val raiz = documento.documentElement.hijo("Root")?.hijo("Group")
    return grupo(requireNotNull(raiz) { "El XML no contiene el grupo raíz" })
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
    for (elemento in documento.documentElement.protegidosDentro()) {
        descifrados[elemento] = flujo.aplicar(Base64.getDecoder().decode(elemento.textContent))
    }

    return XmlLeido(construirArbol(documento, descifrados), documento, descifrados)
}

// Abre una base de datos completa. Las claves y el XML descifrado se borran al terminar,
// también si algo falla. Sobreviven los valores protegidos y la clave compuesta, dentro
// de la base devuelta, hasta que se llame a borrarSecretos.
fun abrirKdbx(datos: ByteArray, contrasena: CharArray): BaseDeDatos {
    val compuesta = claveCompuesta(contrasena)
    try {
        return abrirConCompuesta(datos, compuesta)
    } catch (e: Throwable) {
        compuesta.fill(0)
        throw e
    }
}

internal fun abrirConCompuesta(datos: ByteArray, compuesta: ByteArray): BaseDeDatos {
    val cabecera = leerCabeceraKdbx(datos)
    val claves = derivarClavesConCompuesta(datos, cabecera, compuesta)
    try {
        val contenido = descifrarKdbx(datos, cabecera, claves)
        try {
            val xml = leerXmlKdbx(contenido)
            return BaseDeDatos(cabecera, contenido.adjuntos, xml.documento, xml.protegidos, compuesta)
        } finally {
            contenido.claveInterna.fill(0)
            contenido.xml.fill(0)
        }
    } finally {
        claves.cifrado.fill(0)
        claves.hmac.fill(0)
    }
}
