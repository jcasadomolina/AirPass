package com.jcasadomolina.airpass.crypto

import java.security.MessageDigest

fun sha256(datos: ByteArray): ByteArray {
   return MessageDigest.getInstance("SHA-256").digest(datos)
}

