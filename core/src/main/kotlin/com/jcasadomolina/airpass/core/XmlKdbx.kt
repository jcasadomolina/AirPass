package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.FlujoChaCha20
import com.jcasadomolina.airpass.crypto.sha512
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.IdentityHashMap
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException

// `campos` son los valores normales (título, usuario, URL...). `protegidos` son los
// secretos, ya descifrados y en UTF-8: se guardan como bytes para poder borrarlos.
// `elemento` es su sitio en el documento, que es lo que se modifica al editar.
class Entrada internal constructor(
    val campos: Map<String, String>,
    val protegidos: Map<String, ByteArray>,
    val etiquetas: List<String>,
    val creada: Instant?,
    val modificada: Instant?,
    val contrasenaCambiada: Instant?,
    internal val elemento: Element,
)

/** Valor de un campo de texto de la entrada, esté guardado en claro o protegido; vacío si no lo tiene. */
fun Entrada.texto(campo: String): String = campos[campo] ?: protegidos[campo]?.toString(Charsets.UTF_8).orEmpty()

/** Separa las etiquetas por «;» o «,», quita espacios, vacías y repetidas. */
internal fun limpiarEtiquetas(etiquetas: List<String>): List<String> =
    etiquetas.flatMap { it.split(';', ',') }.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

class Grupo internal constructor(
    val uuid: String,
    val nombre: String,
    val color: String?,
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

internal const val CLAVE_COLOR = "AirPass.Color"
internal val FORMATO_COLOR = Regex("#[0-9A-Fa-f]{6}")

/** Color que AirPass guardó en el CustomData del grupo, o null si no tiene o no es un #RRGGBB válido. */
internal fun Element.colorDeGrupo(): String? =
    hijo("CustomData")?.hijos("Item")
        ?.firstOrNull { it.hijo("Key")?.textContent == CLAVE_COLOR }
        ?.hijo("Value")?.textContent
        ?.takeIf { FORMATO_COLOR.matches(it) }

/**
 * Cuándo se puso la contraseña actual, deducido del historial: la fecha de la primera versión, empezando por
 * la más reciente hacia atrás, a partir de la cual la contraseña ya no cambió. Si nunca cambió, la creación.
 */
private fun fechaCambioContrasena(elemento: Element, protegidos: Map<Element, ByteArray>): Instant? {
    fun contrasena(version: Element): ByteArray? {
        val valor = version.hijos("String").firstOrNull { it.hijo("Key")?.textContent == "Password" }?.hijo("Value")
        return valor?.let { protegidos[it] ?: it.textContent.toByteArray(Charsets.UTF_8) }
    }
    fun modificada(version: Element) = leerFecha(version.hijo("Times")?.hijo("LastModificationTime")?.textContent)

    val versiones = elemento.hijo("History")?.hijos("Entry").orEmpty() + elemento
    val actual = contrasena(elemento)
    for (i in versiones.lastIndex downTo 1) {
        if (!MessageDigest.isEqual(contrasena(versiones[i - 1]), actual)) return modificada(versiones[i])
    }
    return leerFecha(elemento.hijo("Times")?.hijo("CreationTime")?.textContent)
}

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
        val fechas = elemento.hijo("Times")
        return Entrada(
            campos = campos,
            protegidos = secretos,
            etiquetas = limpiarEtiquetas(listOfNotNull(elemento.hijo("Tags")?.textContent)),
            creada = leerFecha(fechas?.hijo("CreationTime")?.textContent),
            modificada = leerFecha(fechas?.hijo("LastModificationTime")?.textContent),
            contrasenaCambiada = fechaCambioContrasena(elemento, protegidos),
            elemento = elemento,
        )
    }

    fun grupo(elemento: Element): Grupo = Grupo(
        uuid = elemento.hijo("UUID")?.textContent.orEmpty(),
        nombre = elemento.hijo("Name")?.textContent.orEmpty(),
        color = elemento.colorDeGrupo(),
        grupos = elemento.hijos("Group").map { grupo(it) },
        entradas = elemento.hijos("Entry").map { entrada(it) },
        elemento = elemento,
    )

    val raiz = documento.documentElement.hijo("Root")?.hijo("Group")
    return grupo(requireNotNull(raiz) { "El XML no contiene el grupo raíz" })
}

private val DOCTYPE = "<!DOCTYPE".toByteArray()

// KeePass escribe el XML en UTF-8 y sin DTD. Un byte nulo delata otra codificación, como
// UTF-16, en la que buscar "<!DOCTYPE" byte a byte no lo encontraría.
internal fun rechazarDtd(xml: ByteArray) {
    require(0.toByte() !in xml) { "El XML del fichero no está en UTF-8" }
    val hayDtd = (0..xml.size - DOCTYPE.size).any { i -> DOCTYPE.indices.all { xml[i + it] == DOCTYPE[it] } }
    require(!hayDtd) { "El XML del fichero contiene una declaración DTD" }
}

internal fun leerXmlKdbx(contenido: ContenidoKdbx): XmlLeido {
    // shortcut: solo ChaCha20; Salsa20 (2) lo usan los ficheros KDBX 3, que no se leen.
    require(contenido.cifradoInterno == CHACHA20_INTERNO) {
        "Cifrado interno no compatible: ${contenido.cifradoInterno}"
    }

    // Sin DTD no hay entidades externas: un XML no puede hacer leer ficheros del disco (XXE).
    // El analizador de Android no admite la opción que prohíbe los DTD, así que la comprobación
    // se hace también a mano, antes y después de analizar, igual en todas las plataformas.
    rechazarDtd(contenido.xml)
    val fabrica = DocumentBuilderFactory.newInstance()
    try {
        fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    } catch (e: ParserConfigurationException) {
        // Android: se confía en las otras dos comprobaciones.
    }
    val analizador = fabrica.newDocumentBuilder()
    analizador.setErrorHandler(null)
    val documento = try {
        analizador.parse(contenido.xml.inputStream())
    } catch (e: SAXException) {
        throw IllegalArgumentException("El XML del fichero no es válido", e)
    }
    require(documento.doctype == null) { "El XML del fichero contiene una declaración DTD" }

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
