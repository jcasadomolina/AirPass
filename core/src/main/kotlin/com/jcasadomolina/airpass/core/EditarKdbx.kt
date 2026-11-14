package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.bytesAleatorios
import com.jcasadomolina.airpass.crypto.contrasenaAleatoria
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.Base64
import org.w3c.dom.Element

// KDBX 4 guarda las fechas como segundos desde el año 1, en un entero de 64 bits en Base64.
private const val SEGUNDOS_DEL_ANO_1_A_1970 = 62_135_596_800L

// Las fechas vienen del fichero: una que no se entiende se muestra como desconocida.
// KDBX 3 las guardaba como texto ISO 8601; se aceptan también.
internal fun leerFecha(texto: String?): Instant? {
    if (texto.isNullOrBlank()) return null
    val bytes = runCatching { Base64.getDecoder().decode(texto) }.getOrNull()
    if (bytes != null && bytes.size == 8) {
        val segundos = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).long
        return runCatching { Instant.ofEpochSecond(segundos - SEGUNDOS_DEL_ANO_1_A_1970) }.getOrNull()
    }
    return runCatching { Instant.parse(texto) }.getOrNull()
}

private fun ahora(): String =
    Base64.getEncoder().encodeToString(enteroLe(Instant.now().epochSecond + SEGUNDOS_DEL_ANO_1_A_1970))

// La interfaz no ve crypto: core le ofrece el generador.
fun generarContrasena(): CharArray = contrasenaAleatoria()

private fun Element.anadir(nombre: String, texto: String? = null): Element {
    val nuevo = ownerDocument.createElement(nombre)
    if (texto != null) nuevo.textContent = texto
    appendChild(nuevo)
    return nuevo
}

// Las entradas van antes que los subgrupos, como las escribe KeePass.
private fun Element.insertarEntrada(entrada: Element) {
    insertBefore(entrada, hijo("Group"))
}

// Devuelve el elemento <Value> del campo, creándolo si la entrada no lo tiene.
private fun Element.valorDe(campo: String): Element {
    val existente = hijos("String").firstOrNull { it.hijo("Key")?.textContent == campo }
    if (existente != null) return existente.hijo("Value") ?: existente.anadir("Value")
    val cadena = anadir("String")
    cadena.anadir("Key", campo)
    return cadena.anadir("Value")
}

private fun BaseDeDatos.ponerContrasena(entrada: Element, contrasena: CharArray) {
    val valor = entrada.valorDe("Password")
    valor.setAttribute("Protected", "True")
    valor.textContent = ""
    protegidos.put(valor, aUtf8(contrasena))?.fill(0)
}

private fun Element.marcarModificada() {
    val fechas = hijo("Times") ?: anadir("Times")
    (fechas.hijo("LastModificationTime") ?: fechas.anadir("LastModificationTime")).textContent = ahora()
}

fun BaseDeDatos.anadirEntrada(grupo: Grupo, titulo: String, usuario: String, contrasena: CharArray) {
    val entrada = documento.createElement("Entry")
    entrada.anadir("UUID", Base64.getEncoder().encodeToString(bytesAleatorios(16)))
    entrada.anadir("IconID", "0")
    val fechas = entrada.anadir("Times")
    for (fecha in listOf("LastModificationTime", "CreationTime", "LastAccessTime", "ExpiryTime", "LocationChanged")) {
        fechas.anadir(fecha, ahora())
    }
    fechas.anadir("Expires", "False")
    fechas.anadir("UsageCount", "0")

    entrada.valorDe("Title").textContent = titulo
    entrada.valorDe("UserName").textContent = usuario
    ponerContrasena(entrada, contrasena)
    entrada.valorDe("URL")
    entrada.valorDe("Notes")

    grupo.elemento.insertarEntrada(entrada)
    refrescar()
}

// Antes de cambiar nada, guarda en el historial de la entrada una copia de cómo estaba,
// como hace KeePass: una contraseña sustituida por error se puede recuperar.
fun BaseDeDatos.modificarEntrada(entrada: Entrada, titulo: String, usuario: String, contrasena: CharArray) {
    val elemento = entrada.elemento

    val historial = elemento.hijo("History") ?: elemento.anadir("History")
    elemento.removeChild(historial)
    val copia = elemento.cloneNode(true) as Element
    // La copia tiene los mismos valores protegidos, en el mismo orden: cada uno recibe su secreto.
    for ((original, clon) in elemento.protegidosDentro().zip(copia.protegidosDentro())) {
        protegidos[clon] = checkNotNull(protegidos[original]).copyOf()
    }
    historial.appendChild(copia)
    elemento.appendChild(historial)

    elemento.valorDe("Title").textContent = titulo
    elemento.valorDe("UserName").textContent = usuario
    ponerContrasena(elemento, contrasena)
    elemento.marcarModificada()
    refrescar()
}

private fun Element.buscarGrupo(uuid: String): Element? {
    for (grupo in hijos("Group")) {
        if (grupo.hijo("UUID")?.textContent == uuid) return grupo
        grupo.buscarGrupo(uuid)?.let { return it }
    }
    return null
}

private fun Element.estaDentroDe(otro: Element): Boolean {
    var actual = parentNode
    while (actual != null) {
        if (actual === otro) return true
        actual = actual.parentNode
    }
    return false
}

// Si la base de datos tiene papelera, la entrada se mueve a ella. Si no la tiene, o la
// entrada ya estaba dentro, se elimina y se deja constancia en DeletedObjects, que es lo
// que permite a otros programas saber que el borrado fue intencionado al sincronizar.
fun BaseDeDatos.borrarEntrada(entrada: Entrada) {
    val elemento = entrada.elemento
    val raizXml = documento.documentElement
    val meta = raizXml.hijo("Meta")
    val root = checkNotNull(raizXml.hijo("Root"))

    val papelera = meta
        ?.takeIf { it.hijo("RecycleBinEnabled")?.textContent.equals("True", ignoreCase = true) }
        ?.hijo("RecycleBinUUID")?.textContent
        ?.let { root.buscarGrupo(it) }

    if (papelera != null && !elemento.estaDentroDe(papelera)) {
        elemento.parentNode.removeChild(elemento)
        papelera.insertarEntrada(elemento)
        val fechas = elemento.hijo("Times") ?: elemento.anadir("Times")
        (fechas.hijo("LocationChanged") ?: fechas.anadir("LocationChanged")).textContent = ahora()
    } else {
        for (secreto in elemento.protegidosDentro()) protegidos.remove(secreto)?.fill(0)
        elemento.parentNode.removeChild(elemento)
        val uuid = elemento.hijo("UUID")?.textContent
        if (uuid != null) {
            val borrado = (root.hijo("DeletedObjects") ?: root.anadir("DeletedObjects")).anadir("DeletedObject")
            borrado.anadir("UUID", uuid)
            borrado.anadir("DeletionTime", ahora())
        }
    }
    refrescar()
}
