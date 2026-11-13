package com.jcasadomolina.airpass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jcasadomolina.airpass.core.BaseDeDatos
import com.jcasadomolina.airpass.core.ClaveIncorrectaException
import com.jcasadomolina.airpass.core.Entrada
import com.jcasadomolina.airpass.core.FicheroManipuladoException
import com.jcasadomolina.airpass.core.Grupo
import com.jcasadomolina.airpass.core.abrirKdbx
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Escritorio() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var base by remember { mutableStateOf<BaseDeDatos?>(null) }
            val abierta = base
            if (abierta == null) {
                PantallaAbrir(alAbrir = { base = it })
            } else {
                PantallaEntradas(
                    raiz = abierta.raiz,
                    alBloquear = {
                        Portapapeles.borrar()
                        abierta.borrarSecretos()
                        base = null
                    },
                )
            }
        }
    }
}

private fun elegirFichero(): File? {
    val dialogo = FileDialog(null as Frame?, "Elegir base de datos", FileDialog.LOAD)
    dialogo.file = "*.kdbx"
    dialogo.isVisible = true
    return dialogo.file?.let { File(dialogo.directory, it) }
}

@Composable
private fun PantallaAbrir(alAbrir: (BaseDeDatos) -> Unit) {
    var fichero by remember { mutableStateOf<File?>(null) }
    // shortcut: el campo de texto de Compose guarda la contraseña en un String, que no se
    // puede borrar de la memoria; sustituirlo por un campo propio al endurecer SR-06.
    var contrasena by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var abriendo by remember { mutableStateOf(false) }
    val ambito = rememberCoroutineScope()

    fun abrir() {
        val elegido = fichero ?: return
        if (abriendo) return
        val caracteres = contrasena.toCharArray()
        contrasena = ""
        error = null
        abriendo = true
        ambito.launch {
            try {
                // La derivación de la clave tarda a propósito: fuera del hilo de la interfaz.
                alAbrir(withContext(Dispatchers.IO) { abrirKdbx(elegido.readBytes(), caracteres) })
            } catch (e: ClaveIncorrectaException) {
                error = "La contraseña no es correcta o el fichero ha sido manipulado."
            } catch (e: FicheroManipuladoException) {
                error = "El contenido del fichero ha sido modificado o está dañado."
            } catch (e: IllegalArgumentException) {
                error = "No se puede abrir el fichero. ${e.message}"
            } catch (e: IOException) {
                error = "No se puede leer el fichero."
            } finally {
                caracteres.fill('\u0000')
                abriendo = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AirPass", style = MaterialTheme.typography.headlineMedium)

        OutlinedButton(onClick = { elegirFichero()?.let { fichero = it; error = null } }) {
            Text("Elegir base de datos…")
        }
        Text(fichero?.name ?: "Ningún fichero elegido", style = MaterialTheme.typography.bodyMedium)

        OutlinedTextField(
            value = contrasena,
            onValueChange = { contrasena = it },
            label = { Text("Contraseña maestra") },
            singleLine = true,
            enabled = !abriendo,
            isError = error != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { abrir() }),
            modifier = Modifier.width(320.dp),
        )

        if (abriendo) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { abrir() }, enabled = fichero != null) { Text("Abrir") }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun entradasConGrupo(grupo: Grupo, ruta: String = grupo.nombre): List<Pair<String, Entrada>> =
    grupo.entradas.map { ruta to it } +
        grupo.grupos.flatMap { entradasConGrupo(it, "$ruta / ${it.nombre}") }

@Composable
private fun PantallaEntradas(raiz: Grupo, alBloquear: () -> Unit) {
    val entradas = remember(raiz) { entradasConGrupo(raiz) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var borradoPendiente by remember { mutableStateOf<Job?>(null) }
    val ambito = rememberCoroutineScope()

    fun copiarContrasena(entrada: Entrada) {
        val contrasena = entrada.protegidos["Password"] ?: return
        Portapapeles.copiarSecreto(contrasena)
        aviso = "Contraseña copiada. Se borrará del portapapeles en $SEGUNDOS_HASTA_BORRAR segundos."
        // Una copia nueva reinicia la cuenta atrás.
        borradoPendiente?.cancel()
        borradoPendiente = ambito.launch {
            delay(SEGUNDOS_HASTA_BORRAR * 1000L)
            Portapapeles.borrar()
            aviso = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("${entradas.size} entradas", style = MaterialTheme.typography.titleMedium)
                aviso?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Button(onClick = alBloquear) { Text("Bloquear") }
        }
        HorizontalDivider()
        LazyColumn {
            items(entradas) { (grupo, entrada) ->
                ListItem(
                    overlineContent = { Text(grupo) },
                    headlineContent = { Text(entrada.campos["Title"].orEmpty().ifEmpty { "(sin título)" }) },
                    supportingContent = { Text(entrada.campos["UserName"].orEmpty()) },
                    trailingContent = {
                        if ("Password" in entrada.protegidos) {
                            TextButton(onClick = { copiarContrasena(entrada) }) { Text("Copiar contraseña") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}
