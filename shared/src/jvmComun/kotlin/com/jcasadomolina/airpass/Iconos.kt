package com.jcasadomolina.airpass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Dibuja una circunferencia completa con dos medios arcos, que es como se hace con los trazados vectoriales. */
private fun PathBuilder.circulo(x: Float, y: Float, radio: Float) {
    moveTo(x - radio, y)
    arcToRelative(radio, radio, 0f, true, true, radio * 2, 0f)
    arcToRelative(radio, radio, 0f, true, true, -radio * 2, 0f)
}

/** Icono de trazo fino de 24×24, el estilo monocromo de las referencias. El color lo pone quien lo muestra. */
private fun icono(nombre: String, grosor: Float = 1.8f, trazos: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = grosor,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = trazos,
        )
    }.build()

object Iconos {
    val Buscar = icono("Buscar") {
        circulo(10.5f, 10.5f, 6.5f)
        moveTo(15.5f, 15.5f)
        lineTo(20f, 20f)
    }
    val Mas = icono("Mas") {
        moveTo(12f, 5f)
        lineTo(12f, 19f)
        moveTo(5f, 12f)
        lineTo(19f, 12f)
    }
    val Atras = icono("Atras") {
        moveTo(19f, 12f)
        lineTo(5f, 12f)
        moveTo(11f, 6f)
        lineTo(5f, 12f)
        lineTo(11f, 18f)
    }
    val Siguiente = icono("Siguiente") {
        moveTo(9.5f, 6f)
        lineTo(15.5f, 12f)
        lineTo(9.5f, 18f)
    }
    val Cerrar = icono("Cerrar") {
        moveTo(6.5f, 6.5f)
        lineTo(17.5f, 17.5f)
        moveTo(17.5f, 6.5f)
        lineTo(6.5f, 17.5f)
    }
    val Opciones = icono("Opciones", grosor = 2.8f) {
        for (y in listOf(5f, 12f, 19f)) {
            moveTo(12f, y)
            lineTo(12f, y + 0.01f)
        }
    }
    val Llave = icono("Llave") {
        circulo(7.5f, 16.5f, 4f)
        moveTo(10.4f, 13.6f)
        lineTo(19.5f, 4.5f)
        moveTo(16.5f, 7.5f)
        lineTo(19f, 10f)
        moveTo(14f, 10f)
        lineTo(15.8f, 11.8f)
    }
    val Regenerar = icono("Regenerar") {
        moveTo(18.5f, 9f)
        arcTo(7f, 7f, 0f, true, false, 19f, 13.5f)
        moveTo(19f, 4.5f)
        lineTo(18.5f, 9f)
        lineTo(14f, 8.5f)
    }
    val Ajustes = icono("Ajustes") {
        moveTo(4f, 7f)
        lineTo(6.6f, 7f)
        moveTo(11.4f, 7f)
        lineTo(20f, 7f)
        moveTo(4f, 17f)
        lineTo(12.6f, 17f)
        moveTo(17.4f, 17f)
        lineTo(20f, 17f)
        circulo(9f, 7f, 2.4f)
        circulo(15f, 17f, 2.4f)
    }
    val Check = icono("Check", grosor = 2.2f) {
        moveTo(5f, 12.5f)
        lineTo(10f, 17.5f)
        lineTo(19f, 7f)
    }
    val Candado = icono("Candado") {
        moveTo(7f, 11f)
        lineTo(17f, 11f)
        arcTo(2f, 2f, 0f, false, true, 19f, 13f)
        lineTo(19f, 19f)
        arcTo(2f, 2f, 0f, false, true, 17f, 21f)
        lineTo(7f, 21f)
        arcTo(2f, 2f, 0f, false, true, 5f, 19f)
        lineTo(5f, 13f)
        arcTo(2f, 2f, 0f, false, true, 7f, 11f)
        moveTo(8f, 11f)
        lineTo(8f, 7.5f)
        arcTo(4f, 4f, 0f, false, true, 16f, 7.5f)
        lineTo(16f, 11f)
    }
    val Huella = icono("Huella", grosor = 1.6f) {
        moveTo(6.2f, 8f)
        arcTo(7.5f, 7.5f, 0f, false, true, 19.3f, 10.5f)
        moveTo(4.5f, 13.5f)
        arcTo(7.5f, 7.5f, 0f, false, true, 5.2f, 10.2f)
        moveTo(8f, 18.5f)
        arcTo(4.5f, 4.5f, 0f, false, true, 7.6f, 11.6f)
        arcTo(4.5f, 4.5f, 0f, false, true, 16.5f, 13f)
        lineTo(16.5f, 14.5f)
        moveTo(12f, 13f)
        lineTo(12f, 15.5f)
        arcTo(5f, 5f, 0f, false, false, 14.5f, 20f)
        moveTo(19.5f, 14f)
        lineTo(19.5f, 15f)
    }
}
