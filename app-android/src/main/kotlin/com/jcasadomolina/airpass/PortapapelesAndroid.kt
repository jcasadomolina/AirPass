package com.jcasadomolina.airpass

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle


fun copiarSecreto(contexto: Context, secreto: ByteArray) {
    // shortcut: ClipData exige un String, que no se puede borrar de la memoria.
    val clip = ClipData.newPlainText("AirPass", secreto.toString(Charsets.UTF_8))
    // Marca el contenido como sensible: Android 13 y posteriores no lo enseñan en el aviso
    // de «copiado» ni en la vista previa del portapapeles. Versiones anteriores lo ignoran.
    clip.description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    contexto.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

fun borrarPortapapeles(contexto: Context) {
    val portapapeles = contexto.getSystemService(ClipboardManager::class.java)
    if (Build.VERSION.SDK_INT >= 28) {
        portapapeles.clearPrimaryClip()
    } else {
        portapapeles.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
