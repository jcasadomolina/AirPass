package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.FlujoChaCha20
import com.jcasadomolina.airpass.crypto.sha512
import java.util.Base64
import java.util.IdentityHashMap
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.SAXException

// `campos` son los valores normales (título, usuario, URL...). `protegidos` son los
// secretos, ya descifrados y en UTF-8: se guardan como bytes para poder borrarlos.
class Entrada(val campos: Map<String, String>, val protegidos: Map<String, ByteArray>)

class Grupo(val nombre: String, val grupos: List<Grupo>, val entradas: List<Entrada>)

private const val CHACHA20 = 3

private fun Element.hijos(nombre: String): List<Element> {
    val encontrados = ArrayList<Element>()
    for (i in 0 until childNodes.length) {
        val nodo = childNodes.item(i)
        if (nodo is Element && nodo.tagName == nombre) encontrados.add(nodo)
    }
    return encontrados
}

fun leerXmlKdbx(contenido: ContenidoKdbx): Grupo {
    // shortcut: solo ChaCha20; Salsa20 (2) lo usan los ficheros KDBX 3, que no se leen.
    require(contenido.cifradoInterno == CHACHA20) {
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
    val material = sha512(contenido.claveInterna)
    val flujo = FlujoChaCha20(clave = material.copyOfRange(0, 32), nonce = material.copyOfRange(32, 44))
    material.fill(0)

    val descifrados = IdentityHashMap<Element, ByteArray>()
    val elementos = documento.getElementsByTagName("*")
    for (i in 0 until elementos.length) {
        val elemento = elementos.item(i) as Element
        if (elemento.getAttribute("Protected").equals("True", ignoreCase = true)) {
            descifrados[elemento] = flujo.aplicar(Base64.getDecoder().decode(elemento.textContent))
        }
    }

    fun entrada(elemento: Element): Entrada {
        val campos = HashMap<String, String>()
        val protegidos = HashMap<String, ByteArray>()
        for (cadena in elemento.hijos("String")) {
            val clave = cadena.hijos("Key").firstOrNull()?.textContent ?: continue
            val valor = cadena.hijos("Value").firstOrNull() ?: continue
            val descifrado = descifrados.remove(valor)
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
    val resultado = grupo(requireNotNull(raiz) { "El XML no contiene el grupo raíz" })

    // shortcut: el historial de cada entrada se descifra pero no se conserva; se borra aquí.
    descifrados.values.forEach { it.fill(0) }
    return resultado
}
