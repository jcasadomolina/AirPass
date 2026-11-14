package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.FlujoChaCha20
import com.jcasadomolina.airpass.crypto.aes256CbcCifrar
import com.jcasadomolina.airpass.crypto.bytesAleatorios
import com.jcasadomolina.airpass.crypto.hmacSha256
import com.jcasadomolina.airpass.crypto.sha256
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

// Cabecera interna y XML, todavía sin cifrar. Vuelve a cifrar los valores protegidos
// con una clave interna nueva, en el orden del documento.
private fun contenidoEnClaro(base: BaseDeDatos): ByteArray {
    val claveInterna = bytesAleatorios(64)
    val flujo = flujoInterno(claveInterna)
    for (elemento in base.documento.documentElement.protegidosDentro()) {
        val secreto = checkNotNull(base.protegidos[elemento]) { "Hay un valor protegido sin descifrar" }
        elemento.textContent = Base64.getEncoder().encodeToString(flujo.aplicar(secreto))
    }

    val salida = ByteArrayOutputStream()
    fun campo(tipo: Int, valor: ByteArray) {
        salida.write(tipo)
        salida.write(enteroLe(valor.size))
        salida.write(valor)
    }
    campo(1, enteroLe(CHACHA20_INTERNO))
    campo(2, claveInterna)
    claveInterna.fill(0)
    for (adjunto in base.adjuntos) campo(3, adjunto)
    campo(0, ByteArray(0))

    base.documento.xmlStandalone = true
    val transformador = TransformerFactory.newInstance().newTransformer()
    transformador.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    transformador.transform(DOMSource(base.documento), StreamResult(salida))
    return salida.toByteArray()
}

// Escribe la base de datos como un fichero KDBX completo. Conserva el cifrado, la KDF y
// todo el contenido del original, y renueva todo lo aleatorio: semilla maestra, vector de
// inicialización, sal de la KDF y clave interna. Así ninguna clave se usa en dos guardados.
// Con `nuevaContrasena` cambia la contraseña maestra; sin ella, se mantiene la actual.
fun guardarKdbx(base: BaseDeDatos, nuevaContrasena: CharArray? = null): ByteArray {
    check(base.abierta) { "La base de datos está bloqueada: sus secretos ya se han borrado" }
    if (nuevaContrasena != null) {
        val nueva = claveCompuesta(nuevaContrasena)
        nueva.copyInto(base.claveCompuesta)
        nueva.fill(0)
    }
    val original = base.cabecera

    val parametros = LinkedHashMap(leerDiccionarioVariante(original.parametrosKdf))
    val salAnterior = requireNotNull(parametros["S"] as? ByteArray) { "Falta la sal de la KDF" }
    parametros["S"] = bytesAleatorios(salAnterior.size)
    val parametrosKdf = escribirDiccionarioVariante(parametros)

    val semillaMaestra = bytesAleatorios(32)
    val iv = bytesAleatorios(original.cifrado.longitudIv)
    val cabecera = escribirCabeceraKdbx(
        original.version, original.cifrado, original.comprimido,
        semillaMaestra, iv, parametrosKdf, original.datosPublicos,
    )

    val transformada = transformarClave(base.claveCompuesta, parametrosKdf)
    val claves = clavesFinales(semillaMaestra, transformada)
    transformada.fill(0)

    try {
        val claro = contenidoEnClaro(base)
        val preparado =
            if (original.comprimido) {
                // Se comprime antes de cifrar: lo cifrado ya no se puede comprimir.
                val comprimido = ByteArrayOutputStream()
                GZIPOutputStream(comprimido).use { it.write(claro) }
                claro.fill(0)
                comprimido.toByteArray()
            } else {
                claro
            }
        val cifrado = when (original.cifrado) {
            Cifrado.AES256 -> aes256CbcCifrar(claves.cifrado, iv, preparado)
            Cifrado.CHACHA20 -> FlujoChaCha20(claves.cifrado, iv).aplicar(preparado)
        }
        preparado.fill(0)

        val claveCabecera = claveDeBloque(-1, claves.hmac)
        val hmacCabecera = hmacSha256(claveCabecera, cabecera)
        claveCabecera.fill(0)

        return cabecera + sha256(cabecera) + hmacCabecera + escribirBloquesAutenticados(cifrado, claves.hmac)
    } finally {
        claves.cifrado.fill(0)
        claves.hmac.fill(0)
    }
}

// Guarda la base de datos en `destino` sin riesgo de dejar el fichero a medias (SR-10).
// 1. Comprueba que lo que se va a escribir se puede volver a abrir.
// 2. Lo escribe en un fichero temporal de la misma carpeta y fuerza su volcado a disco.
// 3. Sustituye el original en una sola operación: o queda el antiguo, o queda el nuevo.
fun guardarEnFichero(base: BaseDeDatos, destino: Path) {
    val datos = guardarKdbx(base)
    abrirConCompuesta(datos, base.claveCompuesta.copyOf()).borrarSecretos()

    val carpeta = destino.toAbsolutePath().parent
    val temporal = Files.createTempFile(carpeta, destino.fileName.toString(), ".tmp")
    try {
        FileChannel.open(temporal, StandardOpenOption.WRITE).use { canal ->
            val buffer = ByteBuffer.wrap(datos)
            while (buffer.hasRemaining()) canal.write(buffer)
            canal.force(true)
        }
        Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temporal)
    }
}
