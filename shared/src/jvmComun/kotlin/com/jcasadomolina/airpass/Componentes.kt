package com.jcasadomolina.airpass

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class EstadoAccion { Reposo, Trabajando, Hecho }

/**
 * Botón píldora que se transforma, como la referencia de descarga: en reposo muestra su texto; mientras trabaja,
 * un indicador y `textoTrabajando`; al terminar se encoge hasta un círculo verde con ✓. El ancho se anima solo.
 */
@Composable
fun BotonAccion(
    texto: String,
    estado: EstadoAccion,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    textoTrabajando: String = texto,
) {
    val hecho = estado == EstadoAccion.Hecho
    val fondo by animateColorAsState(if (hecho) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    val tinta by animateColorAsState(if (hecho) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary)
    Surface(
        onClick = alPulsar,
        enabled = habilitado && estado == EstadoAccion.Reposo,
        shape = RoundedCornerShape(50),
        color = fondo,
        contentColor = tinta,
        shadowElevation = if (habilitado) 6.dp else 0.dp,
        modifier = modifier.height(48.dp).alpha(if (habilitado || estado != EstadoAccion.Reposo) 1f else 0.35f),
    ) {
        AnimatedContent(
            targetState = estado,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(targetScale = 0.85f)) using SizeTransform(clip = false)
            },
            contentAlignment = Alignment.Center,
        ) { actual ->
            when (actual) {
                EstadoAccion.Reposo -> Box(Modifier.height(48.dp).padding(horizontal = 28.dp), Alignment.Center) {
                    Text(texto, style = MaterialTheme.typography.labelLarge)
                }
                EstadoAccion.Trabajando -> Row(
                    modifier = Modifier.height(48.dp).padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = tinta, strokeWidth = 2.dp)
                    Text(textoTrabajando, style = MaterialTheme.typography.labelLarge)
                }
                EstadoAccion.Hecho -> Box(Modifier.size(48.dp), Alignment.Center) {
                    Icon(Iconos.Check, contentDescription = "Hecho", modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/**
 * Candado dibujado a mano que se abre y se cierra con un muelle, como los iconos animados de la referencia:
 * el arco sube y gira sobre su pata izquierda. Se usa en las pantallas de apertura y de bloqueo.
 */
@Composable
fun CandadoAnimado(abierto: Boolean, modifier: Modifier = Modifier, tamano: Dp = 56.dp) {
    val apertura by animateFloatAsState(
        targetValue = if (abierto) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    )
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(modifier.size(tamano)) {
        val u = size.minDimension / 24f
        val trazo = Stroke(width = 1.6f * u, cap = StrokeCap.Round)
        drawRoundRect(color, Offset(5f * u, 11f * u), Size(14f * u, 10f * u), CornerRadius(2.5f * u), style = trazo)
        drawCircle(color, radius = 1.1f * u, center = Offset(12f * u, 15.5f * u))
        drawLine(color, Offset(12f * u, 16f * u), Offset(12f * u, 18f * u), strokeWidth = 1.6f * u, cap = StrokeCap.Round)
        translate(top = -2.5f * u * apertura) {
            rotate(degrees = -28f * apertura, pivot = Offset(8f * u, 11f * u)) {
                val arco = Path().apply {
                    moveTo(8f * u, 11f * u)
                    lineTo(8f * u, 7.5f * u)
                    arcTo(androidx.compose.ui.geometry.Rect(8f * u, 3.5f * u, 16f * u, 11.5f * u), 180f, 180f, false)
                    lineTo(16f * u, 11f * u)
                }
                drawPath(arco, color, style = trazo)
            }
        }
    }
}

/**
 * Tarjeta pulsable que gana sombra al pasar el ratón y se hunde un poco al pulsarla, como la referencia de
 * tarjetas. Se usa para grupos y entradas.
 */
@Composable
fun TarjetaPulsable(
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitada: Boolean = true,
    contenido: @Composable RowScope.() -> Unit,
) {
    val interaccion = remember { MutableInteractionSource() }
    val encima by interaccion.collectIsHoveredAsState()
    val pulsada by interaccion.collectIsPressedAsState()
    val sombra by animateDpAsState(
        when {
            pulsada -> 1.dp
            encima -> 10.dp
            else -> 2.dp
        },
    )
    val escala by animateFloatAsState(if (pulsada) 0.98f else 1f)
    Surface(
        onClick = alPulsar,
        enabled = habilitada,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = sombra,
        interactionSource = interaccion,
        modifier = modifier.graphicsLayer {
            scaleX = escala
            scaleY = escala
        }.hoverable(interaccion),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = contenido,
        )
    }
}

/**
 * Tarjeta con cabecera que se despliega, como la referencia de ajustes. La cabecera lleva un interruptor
 * (`conInterruptor`) o una flecha que gira; `resumen` es un texto corto a la derecha, como «4 de 10», que
 * se anima al cambiar.
 */
@Composable
fun TarjetaDesplegable(
    titulo: String,
    desplegada: Boolean,
    alCambiar: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    resumen: String? = null,
    conInterruptor: Boolean = false,
    contenido: @Composable ColumnScope.() -> Unit,
) {
    val giro by animateFloatAsState(if (desplegada) 90f else 0f)
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = modifier) {
        Column(Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .background(if (desplegada) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                    .toggleable(value = desplegada, role = if (conInterruptor) Role.Switch else Role.Button, onValueChange = alCambiar)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (resumen != null) {
                    AnimatedContent(
                        targetState = resumen,
                        transitionSpec = { slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut() },
                    ) {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                if (conInterruptor) {
                    Switch(
                        checked = desplegada,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.secondary,
                            checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    )
                } else {
                    Icon(Iconos.Siguiente, contentDescription = null, modifier = Modifier.size(20.dp).rotate(giro))
                }
            }
            AnimatedVisibility(visible = desplegada, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(top = 4.dp), content = contenido)
            }
        }
    }
}

/** Casilla redonda de la referencia de ajustes: un aro vacío que, al marcarse, se rellena con un ✓ y rebota. */
@Composable
fun CasillaRedonda(marcada: Boolean, modifier: Modifier = Modifier) {
    val relleno by animateColorAsState(if (marcada) MaterialTheme.colorScheme.onSurface else Color.Transparent)
    val escala by animateFloatAsState(
        targetValue = if (marcada) 1f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
    )
    Box(
        modifier = modifier.size(22.dp).graphicsLayer {
            scaleX = escala
            scaleY = escala
        }.clip(CircleShape).background(relleno)
            .border(1.5.dp, if (marcada) Color.Transparent else MaterialTheme.colorScheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = marcada, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Iconos.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(14.dp))
        }
    }
}

/** Fila con texto y casilla redonda que se marca pulsando en cualquier parte de ella. */
@Composable
fun FilaCasilla(texto: String, marcada: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .toggleable(value = marcada, role = Role.Checkbox, onValueChange = alCambiar)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        CasillaRedonda(marcada)
    }
}

private const val BITS_PARA_VERDE = 128.0
private val ROJO = Color(0xFFE5484D)
private val AMARILLO = Color(0xFFF5B83D)
private val VERDE = Color(0xFF2BB673)

/** Color de la fuerza: de rojo a amarillo en la primera mitad y de amarillo a verde en la segunda. */
private fun colorDeFuerza(fraccion: Float): Color =
    if (fraccion < 0.5f) lerp(ROJO, AMARILLO, fraccion * 2) else lerp(AMARILLO, VERDE, (fraccion - 0.5f) * 2)

/** Nombre del nivel de fuerza según los bits, para leerlo sin interpretar el número. */
private fun nivelDeFuerza(bits: Double): String = when {
    bits < 40 -> "Muy débil"
    bits < 60 -> "Débil"
    bits < 80 -> "Aceptable"
    bits < 100 -> "Fuerte"
    else -> "Muy fuerte"
}

/** Barra fina que se llena y cambia de rojo a verde según los bits; la versión discreta del medidor, sin texto. */
@Composable
fun BarraFuerza(bits: Double, modifier: Modifier = Modifier) {
    val fraccion by animateFloatAsState((bits / BITS_PARA_VERDE).coerceIn(0.0, 1.0).toFloat(), tween(350))
    val color by animateColorAsState(colorDeFuerza(fraccion))
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(fraccion).height(6.dp).clip(RoundedCornerShape(50)).background(color))
    }
}

/** Barra superior de las pantallas secundarias: flecha para volver, título y acciones a la derecha. */
@Composable
fun BarraSuperior(titulo: String, alVolver: () -> Unit, acciones: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = alVolver) { Icon(Iconos.Atras, contentDescription = "Volver") }
        Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        acciones()
    }
}

/** Tarjeta blanca de sección con un título pequeño encima del contenido, para agrupar campos en una pantalla. */
@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            contenido()
        }
    }
}

/**
 * Medidor semicircular de la fuerza de una contraseña, como el de la puntuación de la referencia de gráficas:
 * un arco que se llena de rojo a verde (verde a partir de 128 bits) con los bits y el nivel en el centro.
 */
@Composable
fun MedidorFuerza(bits: Double, modifier: Modifier = Modifier) {
    val fraccion by animateFloatAsState((bits / BITS_PARA_VERDE).coerceIn(0.0, 1.0).toFloat(), tween(450))
    val pista = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.width(220.dp).height(124.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.width(220.dp).height(124.dp)) {
            val grosor = 16.dp.toPx()
            val lado = size.width - grosor
            val arco = Size(lado, lado)
            val origen = Offset(grosor / 2, grosor / 2)
            val estilo = Stroke(width = grosor, cap = StrokeCap.Round)
            drawArc(pista, 180f, 180f, false, origen, arco, style = estilo)
            if (fraccion > 0f) drawArc(colorDeFuerza(fraccion), 180f, 180f * fraccion, false, origen, arco, style = estilo)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${bits.roundToInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("bits · ${nivelDeFuerza(bits)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
