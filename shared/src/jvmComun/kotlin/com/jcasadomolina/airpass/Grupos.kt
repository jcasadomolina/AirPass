package com.jcasadomolina.airpass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.BaseDeDatos
import com.jcasadomolina.airpass.core.Entrada
import com.jcasadomolina.airpass.core.Grupo
import com.jcasadomolina.airpass.core.anadirGrupo

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
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (ruta.isNotEmpty()) TextButton(onClick = { subir() }) { Text("←") }
                if (buscando) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        placeholder = { Text("Buscar por título en ${actual.nombre}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(foco),
                    )
                    LaunchedEffect(Unit) { foco.requestFocus() }
                    TextButton(onClick = { cerrarBusqueda() }) { Text("✕") }
                } else {
                    Text(
                        grupos.joinToString(" / ") { it.nombre },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    TextButton(onClick = { buscando = true }) { Text("🔍") }
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (buscando && busqueda.isNotBlank()) {
                    items(actual.buscar(busqueda)) { FilaEntrada(it, habilitado, alAbrirEntrada, accionesEntrada) }
                } else {
                    items(actual.grupos, key = { it.uuid }) { grupo ->
                        ListItem(
                            modifier = Modifier.clickable { ruta = ruta + grupo.uuid },
                            leadingContent = { Box(Modifier.size(16.dp).background(colorDe(grupo.color), CircleShape)) },
                            headlineContent = { Text(grupo.nombre) },
                            supportingContent = {
                                val total = grupo.totalEntradas()
                                Text(if (total == 1) "1 entrada" else "$total entradas")
                            },
                            trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
                        )
                        HorizontalDivider()
                    }
                    items(actual.entradas) { FilaEntrada(it, habilitado, alAbrirEntrada, accionesEntrada) }
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            FloatingActionButton(onClick = { if (habilitado) menu = true }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
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

/** Una entrada de la lista: título y usuario, y los botones que añada la plataforma. */
@Composable
private fun FilaEntrada(
    entrada: Entrada,
    habilitado: Boolean,
    alAbrir: (Entrada) -> Unit,
    acciones: @Composable (Entrada) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(enabled = habilitado) { alAbrir(entrada) },
        headlineContent = { Text(entrada.campos["Title"].orEmpty().ifEmpty { "(sin título)" }) },
        supportingContent = { Text(entrada.campos["UserName"].orEmpty()) },
        trailingContent = { acciones(entrada) },
    )
    HorizontalDivider()
}

/** Diálogo para crear un grupo: pide el nombre y deja elegir un color de la paleta. */
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
                        val borde = if (opcion == color) {
                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        }
                        Box(Modifier.size(24.dp).background(colorDe(opcion), CircleShape).then(borde).clickable { color = opcion })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { alAceptar(nombre.trim(), color) }, enabled = nombre.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = alCancelar) { Text("Cancelar") } },
    )
}
