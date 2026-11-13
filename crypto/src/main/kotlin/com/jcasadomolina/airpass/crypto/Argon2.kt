package com.jcasadomolina.airpass.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

enum class TipoArgon2 { D, ID }

// Argon2 (RFC 9106) con la implementación de BouncyCastle: el JDK no lo incluye.
// Devuelve 32 bytes. La memoria se indica en KiB.
fun argon2(
    tipo: TipoArgon2,
    clave: ByteArray,
    sal: ByteArray,
    iteraciones: Int,
    memoriaKiB: Int,
    paralelismo: Int,
    version: Int = Argon2Parameters.ARGON2_VERSION_13,
    secreto: ByteArray? = null,
    datosAsociados: ByteArray? = null,
): ByteArray {
    require(sal.size >= 8) { "La sal de Argon2 debe medir al menos 8 bytes" }
    require(iteraciones >= 1) { "Número de iteraciones de Argon2 no válido: $iteraciones" }
    require(paralelismo >= 1) { "Paralelismo de Argon2 no válido: $paralelismo" }
    require(memoriaKiB >= 8 * paralelismo) { "Memoria de Argon2 insuficiente: $memoriaKiB KiB" }
    require(version == Argon2Parameters.ARGON2_VERSION_10 || version == Argon2Parameters.ARGON2_VERSION_13) {
        "Versión de Argon2 no compatible: $version"
    }

    val parametros = Argon2Parameters.Builder(
        if (tipo == TipoArgon2.D) Argon2Parameters.ARGON2_d else Argon2Parameters.ARGON2_id,
    )
        .withVersion(version)
        .withSalt(sal)
        .withIterations(iteraciones)
        .withMemoryAsKB(memoriaKiB)
        .withParallelism(paralelismo)
    if (secreto != null) parametros.withSecret(secreto)
    if (datosAsociados != null) parametros.withAdditional(datosAsociados)

    val generador = Argon2BytesGenerator()
    generador.init(parametros.build())
    val salida = ByteArray(32)
    generador.generateBytes(clave, salida)
    return salida
}
