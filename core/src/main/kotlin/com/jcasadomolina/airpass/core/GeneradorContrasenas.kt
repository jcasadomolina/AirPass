package com.jcasadomolina.airpass.core

import com.jcasadomolina.airpass.crypto.contrasenaAleatoria
import kotlin.math.log2

enum class Caracteres(val nombre: String, val juego: String) {
    MAYUSCULAS("Mayúsculas (A-Z)", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
    MINUSCULAS("Minúsculas (a-z)", "abcdefghijklmnopqrstuvwxyz"),
    DIGITOS("Dígitos (0-9)", "0123456789"),
    MENOS("Menos (-)", "-"),
    SUBRAYADO("Subrayado (_)", "_"),
    ESPACIO("Espacio", " "),
    ESPECIAL("Especiales (!\"#\$%&'*+./:;=?@\\^`|~)", "!\"#\$%&'*+./:;=?@\\^`|~"),
    EXTENDIDO("Especiales extendidos (¡¿ñüß© y otros)", (0xA1..0xFF).filter { it != 0xAD }.map { it.toChar() }.joinToString("")),
    PARENTESIS("Paréntesis y corchetes ()[]{}<>", "()[]{}<>"),
    COMA("Coma (,)", ","),
}

const val PARECIDOS = "O0Il1|"

data class OpcionesGenerador(
    val longitud: Int = 16,
    val caracteres: Set<Caracteres> = setOf(Caracteres.MAYUSCULAS, Caracteres.MINUSCULAS, Caracteres.DIGITOS, Caracteres.ESPECIAL),
    val excluirParecidos: Boolean = false,
    val unoDeCada: Boolean = true,
)

/** Los juegos de caracteres elegidos, en el orden del enum, sin los parecidos si se pide y sin los que queden vacíos. */
private fun OpcionesGenerador.grupos(): List<String> =
    Caracteres.entries.filter { it in caracteres }
        .map { grupo -> if (excluirParecidos) grupo.juego.filterNot { it in PARECIDOS } else grupo.juego }
        .filter { it.isNotEmpty() }

/** Motivo por el que no se puede generar con estas opciones, o null si se puede. */
fun OpcionesGenerador.problema(): String? = when {
    grupos().isEmpty() -> "Elige al menos un tipo de carácter."
    longitud !in 1..256 -> "La longitud debe estar entre 1 y 256."
    unoDeCada && longitud < grupos().size -> "Con «al menos uno de cada grupo», la longitud debe ser al menos ${grupos().size}."
    else -> null
}

/**
 * Bits de entropía: longitud × log2(número de caracteres posibles). Es lo que costaría adivinarla a quien
 * conoce las opciones. Con «uno de cada grupo» la cifra real es algo menor; KeePass y KeePassXC la calculan igual.
 */
fun OpcionesGenerador.bits(): Double {
    val posibles = grupos().joinToString("").toSet().size
    return if (posibles == 0) 0.0 else longitud * log2(posibles.toDouble())
}

/** Genera una contraseña con las opciones dadas, o con las de por defecto. Quien la recibe debe borrarla. */
fun generarContrasena(opciones: OpcionesGenerador = OpcionesGenerador()): CharArray =
    contrasenaAleatoria(opciones.grupos(), opciones.longitud, opciones.unoDeCada)
