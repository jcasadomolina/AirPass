# 15 · La interfaz y los secretos

## Idea clave
La criptografía puede ser perfecta y el secreto escaparse igual por la interfaz: por el campo donde se escribe, por el portapapeles o por un mensaje de error. La interfaz debe tener los secretos en la mano el menor tiempo posible.

## Una sola puerta de entrada
La interfaz no encadena los pasos del lector ni toca claves. Llama a una función de `core`:

```kotlin
val raiz = abrirKdbx(datos, contrasena)
```

`abrirKdbx` usa `try`/`finally` para rellenar con ceros las claves de cifrado, la clave interna y el XML descifrado al terminar, **también si algo falla a mitad**. Si esa limpieza dependiera de la interfaz, bastaría olvidarla en una pantalla para dejar las claves en memoria.

`app-desktop` depende de `core`, pero no de `crypto` ([tema 08](08-modulos-de-gradle.md)): aunque quisiera, no puede derivar una clave por su cuenta.

## No bloquear la ventana
La KDF tarda a propósito ([tema 03](03-derivacion-de-claves.md)). Ejecutada en el hilo de la interfaz, la ventana se congela y el sistema la marca como «no responde». Se ejecuta en otro hilo:

```kotlin
ambito.launch {
    val raiz = withContext(Dispatchers.IO) { abrirKdbx(fichero.readBytes(), caracteres) }
}
```

## Cada error, su mensaje
| Excepción | Qué se dice al usuario |
|---|---|
| `ClaveIncorrectaException` | La contraseña no es correcta o el fichero ha sido manipulado |
| `FicheroManipuladoException` | El contenido ha sido modificado o está dañado |
| `IllegalArgumentException` | No se puede abrir el fichero, con el detalle |
| `IOException` | No se puede leer el fichero |

Para esto servía distinguir los tipos de error en `core`. Un mensaje de error nunca incluye el secreto ni partes de él.

## El límite de la plataforma: `String`
El campo de texto de Compose guarda lo que se escribe en un `String`, y el portapapeles de Java también exige uno. Un `String` es inmutable: no se puede rellenar con ceros y permanece en memoria hasta que el recolector lo elimine, quizá con copias.

AirPass convierte la contraseña a `CharArray` y la borra tras usarla, pero el `String` del campo ya existió. Es la consecuencia que el ADR-001 anticipaba al elegir una plataforma con recolector de basura. La solución es un campo de entrada propio que escriba directamente en un array.

## El portapapeles
Es un espacio compartido que **cualquier programa puede leer** sin pedir permiso. En Windows hay dos agravantes:

- el **historial** (Win+V) conserva lo copiado aunque después se borre el portapapeles;
- la **sincronización en la nube** puede enviarlo a otros dispositivos.

El portapapeles guarda varios *formatos* a la vez. Windows define tres con los que una aplicación pide que lo copiado no se conserve:

| Formato | Valor | Significado |
|---|---|---|
| `ExcludeClipboardContentFromMonitorProcessing` | 1 | Que los gestores de portapapeles lo ignoren |
| `CanIncludeInClipboardHistory` | 0 | Fuera del historial |
| `CanUploadToCloudClipboard` | 0 | Fuera de la sincronización |

Son **peticiones, no garantías**: Windows las respeta; un programa malicioso, no.

## Cómo lo hace KeePass
Borra el portapapeles a los 12 segundos, añade esos mismos formatos y, antes de borrar, comprueba que el contenido sigue siendo el suyo.

## Qué hace AirPass
- Pantalla de apertura: selector de fichero, campo de contraseña y botón, con indicador de progreso.
- Lista de entradas con el grupo, el título y el usuario. Las contraseñas no se muestran.
- **Copiar contraseña**: añade los tres formatos, borra a los 12 segundos y reinicia la cuenta si se copia otra.
- **Borra solo lo suyo**: Windows avisa (`lostOwnership`) cuando otra aplicación sustituye el contenido, y desde entonces AirPass no borra nada.
- **Bloquear** borra el portapapeles y pone a cero las contraseñas en memoria (`borrarSecretos`). Cerrar la ventana también borra el portapapeles.

**Pendiente**
- Campo de contraseña que no pase por `String`.
- Bloqueo automático por inactividad (RF-06).
- Tope de tamaño del fichero que se abre.
- La pantalla existe solo en escritorio.
- Si la aplicación se cierra a la fuerza, el borrado del portapapeles no se ejecuta.

## Preguntas de repaso
1. ¿Por qué la limpieza de las claves está en `core` y no en la pantalla?
2. ¿Qué pasaría si la KDF se ejecutara en el hilo de la interfaz?
3. ¿Por qué no basta con borrar el portapapeles a los 12 segundos en Windows?
4. ¿Por qué AirPass comprueba si el contenido sigue siendo suyo antes de borrarlo?
5. ¿Por qué un `String` es un mal sitio para una contraseña?
