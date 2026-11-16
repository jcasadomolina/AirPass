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

class DatosEntrada(
    val titulo: String,
    val usuario: String,
    val url: String = "",
    val notas: String = "",
    val etiquetas: List<String> = emptyList(),
)

/** Escribe un campo de texto; si el fichero lo marca como protegido, el valor va con los demás secretos y no en claro. */
private fun BaseDeDatos.ponerTexto(entrada: Element, campo: String, texto: String) {
    val valor = entrada.valorDe(campo)
    if (valor.esProtegido()) {
        valor.textContent = ""
        protegidos.put(valor, texto.toByteArray(Charsets.UTF_8))?.fill(0)
    } else {
        valor.textContent = texto
    }
}

/** Escribe título, usuario, URL, notas y etiquetas; las etiquetas van en <Tags> separadas por «;», como en KeePass. */
private fun BaseDeDatos.ponerDatos(entrada: Element, datos: DatosEntrada) {
    ponerTexto(entrada, "Title", datos.titulo)
    ponerTexto(entrada, "UserName", datos.usuario)
    ponerTexto(entrada, "URL", datos.url)
    ponerTexto(entrada, "Notes", datos.notas)
    (entrada.hijo("Tags") ?: entrada.anadir("Tags")).textContent = limpiarEtiquetas(datos.etiquetas).joinToString(";")
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

/** Añade a un elemento nuevo su UUID aleatorio, el icono y el bloque de fechas, todas con la hora actual. */
private fun Element.anadirIdentidad(icono: String) {
    anadir("UUID", Base64.getEncoder().encodeToString(bytesAleatorios(16)))
    anadir("IconID", icono)
    val fechas = anadir("Times")
    for (fecha in listOf("LastModificationTime", "CreationTime", "LastAccessTime", "ExpiryTime", "LocationChanged")) {
        fechas.anadir(fecha, ahora())
    }
    fechas.anadir("Expires", "False")
    fechas.anadir("UsageCount", "0")
}

/**
 * Crea un grupo vacío dentro de `padre`, con su nombre y su color en formato #RRGGBB. El color va
 * en el CustomData del grupo, que KeePass y KeePassXC conservan al guardar aunque no lo muestren.
 */
fun BaseDeDatos.anadirGrupo(padre: Grupo, nombre: String, color: String) {
    require(FORMATO_COLOR.matches(color)) { "Color no válido: $color" }
    val grupo = documento.createElement("Group")
    grupo.anadirIdentidad(icono = "48")
    grupo.anadir("Name", nombre)
    grupo.anadir("IsExpanded", "True")
    val item = grupo.anadir("CustomData").anadir("Item")
    item.anadir("Key", CLAVE_COLOR)
    item.anadir("Value", color)
    padre.elemento.appendChild(grupo)
    refrescar()
}

/** Crea una entrada en el grupo con sus datos y su contraseña. */
fun BaseDeDatos.anadirEntrada(grupo: Grupo, datos: DatosEntrada, contrasena: CharArray) {
    val entrada = documento.createElement("Entry")
    entrada.anadirIdentidad(icono = "0")

    ponerDatos(entrada, datos)
    ponerContrasena(entrada, contrasena)

    grupo.elemento.insertarEntrada(entrada)
    refrescar()
}

// Antes de cambiar nada, guarda en el historial de la entrada una copia de cómo estaba,
// como hace KeePass: una contraseña sustituida por error se puede recuperar.
fun BaseDeDatos.modificarEntrada(entrada: Entrada, datos: DatosEntrada, contrasena: CharArray) {
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

    ponerDatos(elemento, datos)
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
