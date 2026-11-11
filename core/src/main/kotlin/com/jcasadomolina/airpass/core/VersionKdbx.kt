package com.jcasadomolina.airpass.core

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class VersionKdbx (val menor: Int, val mayor: Int) 

fun leerVersionKdbx(datos: ByteArray): VersionKdbx {
    require(datos.size >= 12) { "El fichero es demasiado corto para ser un KDBX" }

    val lector = ByteBuffer.wrap(datos).order(ByteOrder.LITTLE_ENDIAN)
    val firma1 = lector.getInt()
    val firma2 = lector.getInt()
    require(firma1.compareTo(0x9AA2D903.toInt()) == 0) {"El valor de firma1 no es correcto"}
    require(firma2.compareTo(0xB54BFB67.toInt()) == 0) {"El valor de firma2 no es correcto"}
    val menor = lector.getShort().toInt()
    val mayor = lector.getShort().toInt()
    require(mayor.compareTo(4) == 0) {"El valor mayor no es 4"}

    return VersionKdbx(mayor, menor)
}
