package com.jcasadomolina.airpass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.BaseDeDatos
import com.jcasadomolina.airpass.core.Entrada
import com.jcasadomolina.airpass.core.Grupo
import com.jcasadomolina.airpass.core.anadirGrupo
import com.jcasadomolina.airpass.core.texto

val COLORES_DE_GRUPO = listOf("#E53935", "#FB8C00", "#FDD835", "#43A047", "#00897B", "#1E88E5", "#5E35B1", "#757575")

/** Número de entradas del grupo, contando también las de todos sus subgrupos. */
fun Grupo.totalEntradas(): Int = entradas.size + grupos.sumOf { it.totalEntradas() }

/** Entradas del grupo y de sus subgrupos cuyo título contiene el texto, sin distinguir mayúsculas. */
private fun Grupo.buscar(texto: String): List<Entrada> =
    entradas.filter { it.campos["Title"].orEmpty().contains(texto, ignoreCase = true) } + grupos.flatMap { it.buscar(texto) }

/** Convierte un color #RRGGBB, ya validado por core, en un Color de Compose; gris si el grupo no tiene. */
private fun colorDe(hex: String?): Color = hex?.let { Color(0xFF000000 or it.substring(1).toLong(16)) } ?: Color.Gray

/** Grupos desde la raíz hasta el actual, siguiendo los UUID de la ruta; se detiene si alguno ya no existe. */
private fun camino(raiz: Grupo, ruta: List<String>): List<Grupo> {
    val camino = mutableListOf(raiz)
    for (uuid in ruta) camino += camino.last().grupos.firstOrNull { it.uuid == uuid } ?: break
    return camino
}

/**
 * Pantalla común de escritorio y Android para recorrer la base por grupos. Muestra los subgrupos del
 * grupo actual (color, nombre, número de entradas y ›) y sus entradas, permite buscar por título dentro
 * del grupo y crear grupos y entradas con el botón +. La ruta se guarda como UUID y no como objetos
 * Grupo, porque core rehace el árbol después de cada cambio. `raiz` debe ser la de la última versión
 * de la base, y `accionesEntrada` deja a cada plataforma añadir botones a cada entrada.
 */
@Composable
fun NavegadorGrupos(
    base: BaseDeDatos,
    raiz: Grupo,
    habilitado: Boolean,
    alCambiar: () -> Unit,
    alAbrirEntrada: (Entrada) -> Unit,
    alNuevaEntrada: (Grupo) -> Unit,
    modifier: Modifier = Modifier,
    accionesEntrada: @Composable (Entrada) -> Unit = {},
) {
    var ruta by remember { mutableStateOf(listOf<String>()) }
    var buscando by remember { mutableStateOf(false) }
    var busqueda by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var creandoGrupo by remember { mutableStateOf(false) }
    val foco = remember { FocusRequester() }
    val grupos = camino(raiz, ruta)
    val actual = grupos.last()

    fun cerrarBusqueda() {
        buscando = false
        busqueda = ""
    }

    fun subir() {
        cerrarBusqueda()
        ruta = ruta.dropLast(1)
    }

    AtrasDelSistema(activo = buscando || ruta.isNotEmpty()) { if (buscando) cerrarBusqueda() else subir() }

    Box(modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = ruta.isNotEmpty()) {
                    IconButton(onClick = { subir() }) { Icon(Iconos.Atras, contentDescription = "Volver al grupo anterior") }
                }
                if (buscando) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        placeholder = { Text("Buscar en ${actual.nombre}") },
                        leadingIcon = { Icon(Iconos.Buscar, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).focusRequester(foco),
                    )
                    LaunchedEffect(Unit) { foco.requestFocus() }
                    IconButton(onClick = { cerrarBusqueda() }) { Icon(Iconos.Cerrar, contentDescription = "Cerrar la búsqueda") }
                } else {
                    Text(
                        grupos.joinToString("  /  ") { it.nombre },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { buscando = true }) { Icon(Iconos.Buscar, contentDescription = "Buscar por título") }
                }
            }
            val resultados = if (buscando && busqueda.isNotBlank()) actual.buscar(busqueda) else null
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (resultados != null) {
                    items(resultados) { FilaEntrada(it, habilitado, alAbrirEntrada, accionesEntrada) }
                    if (resultados.isEmpty()) item { Vacio("Ninguna entrada de ${actual.nombre} contiene «$busqueda» en el título.") }
                } else {
                    items(actual.grupos, key = { it.uuid }) { grupo ->
                        TarjetaPulsable(alPulsar = { ruta = ruta + grupo.uuid }, modifier = Modifier.fillMaxWidth().animateItem()) {
                            val color = colorDe(grupo.color)
                            Box(
                                Modifier.size(38.dp).clip(MaterialTheme.shapes.small).background(color.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(12.dp).background(color, CircleShape))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(grupo.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                val total = grupo.totalEntradas()
                                Text(
                                    if (total == 1) "1 entrada" else "$total entradas",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Iconos.Siguiente, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    items(actual.entradas) { FilaEntrada(it, habilitado, alAbrirEntrada, accionesEntrada) }
                    if (actual.grupos.isEmpty() && actual.entradas.isEmpty()) {
                        item { Vacio("Este grupo está vacío. Pulsa «Añadir» para crear una entrada o un grupo.") }
                    }
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)) {
            Surface(
                onClick = { if (habilitado) menu = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(start = 18.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Iconos.Mas, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Añadir", style = MaterialTheme.typography.labelLarge)
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Nueva entrada") }, onClick = { menu = false; alNuevaEntrada(actual) })
                DropdownMenuItem(text = { Text("Nuevo grupo") }, onClick = { menu = false; creandoGrupo = true })
            }
        }
    }

    if (creandoGrupo) {
        DialogoGrupo(
            alAceptar = { nombre, color ->
                base.anadirGrupo(actual, nombre, color)
                creandoGrupo = false
                alCambiar()
            },
            alCancelar = { creandoGrupo = false },
        )
    }
}

/** Mensaje centrado y discreto para una lista sin elementos. */
@Composable
private fun Vacio(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp),
    )
}

/** Una entrada de la lista como tarjeta: inicial del título, título y usuario, y los botones que añada la plataforma. */
@Composable
private fun FilaEntrada(
    entrada: Entrada,
    habilitado: Boolean,
    alAbrir: (Entrada) -> Unit,
    acciones: @Composable (Entrada) -> Unit,
) {
    val titulo = entrada.texto("Title").ifEmpty { "(sin título)" }
    TarjetaPulsable(alPulsar = { alAbrir(entrada) }, habilitada = habilitado, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(titulo.first().uppercase(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entrada.texto("UserName"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        acciones(entrada)
    }
}

/** Diálogo para crear un grupo: pide el nombre y deja elegir un color de la paleta; el elegido lleva un ✓. */
@Composable
private fun DialogoGrupo(alAceptar: (nombre: String, color: String) -> Unit, alCancelar: () -> Unit) {
    var nombre by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(COLORES_DE_GRUPO.first()) }

    AlertDialog(
        onDismissRequest = alCancelar,
        title = { Text("Nuevo grupo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (opcion in COLORES_DE_GRUPO) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(colorDe(opcion)).clickable { color = opcion },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (opcion == color) Icon(Iconos.Check, contentDescription = "Elegido", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { alAceptar(nombre.trim(), color) }, enabled = nombre.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
