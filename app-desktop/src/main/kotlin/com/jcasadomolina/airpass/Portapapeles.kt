package com.jcasadomolina.airpass

import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.SystemFlavorMap
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream

// Mismo plazo que KeePass.
const val SEGUNDOS_HASTA_BORRAR = 12

// Formatos con los que una aplicación pide a Windows que no guarde lo copiado en el
// historial del portapapeles (Win+V) ni lo sincronice con otros dispositivos.
// Cada uno es un entero de 32 bits.
private val exclusiones: Map<DataFlavor, ByteArray> = run {
    val mapa = SystemFlavorMap.getDefaultFlavorMap() as SystemFlavorMap
    listOf(
        "ExcludeClipboardContentFromMonitorProcessing" to 1,
        "CanIncludeInClipboardHistory" to 0,
        "CanUploadToCloudClipboard" to 0,
    ).associate { (formato, valor) ->
        val sabor = DataFlavor("application/x-airpass-${formato.lowercase()}; class=java.io.InputStream", formato)
        mapa.addUnencodedNativeForFlavor(sabor, formato)
        sabor to byteArrayOf(valor.toByte(), 0, 0, 0)
    }
}

private class Secreto(private val texto: String) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> =
        (listOf(DataFlavor.stringFlavor) + exclusiones.keys).toTypedArray()

    override fun isDataFlavorSupported(sabor: DataFlavor): Boolean =
        sabor == DataFlavor.stringFlavor || sabor in exclusiones

    override fun getTransferData(sabor: DataFlavor): Any = when {
        sabor == DataFlavor.stringFlavor -> texto
        else -> ByteArrayInputStream(exclusiones[sabor] ?: throw UnsupportedFlavorException(sabor))
    }
}

object Portapapeles : ClipboardOwner {
    private val sistema: Clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    // Lo último que copió AirPass, mientras siga siendo lo que hay en el portapapeles.
    private var nuestro: Transferable? = null

    fun copiarSecreto(secreto: ByteArray) {
        // shortcut: el portapapeles de Java exige un String, que no se puede borrar de la memoria.
        val contenido = Secreto(secreto.toString(Charsets.UTF_8))
        nuestro = contenido
        sistema.setContents(contenido, this)
    }

    // Solo borra si el contenido sigue siendo el de AirPass: no pisa lo que el usuario copie después.
    fun borrar() {
        if (nuestro == null) return
        nuestro = null
        sistema.setContents(StringSelection(""), null)
    }

    // Windows avisa cuando otra aplicación sustituye el contenido.
    override fun lostOwnership(portapapeles: Clipboard, contenido: Transferable) {
        if (contenido === nuestro) nuestro = null
    }
}
