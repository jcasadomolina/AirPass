# 18 · Escribir un fichero sin perder nada

## Idea clave
Leer mal un fichero es un error; escribirlo mal **destruye los datos del usuario**. Un escritor tiene dos obligaciones que un lector no tiene: conservar lo que no entiende y no reutilizar nunca nada aleatorio.

## Conservar lo que no se entiende
AirPass solo interpreta grupos, títulos, usuarios y contraseñas. Un fichero real contiene mucho más: fechas, identificadores, iconos, etiquetas, historial, adjuntos, la papelera y datos de otros programas.

Si el fichero se escribiera a partir de lo que AirPass entiende, abrir una base de datos y guardarla borraría todo lo demás, **sin ningún error**. Por eso se guarda en memoria el documento XML completo tal como se leyó y se vuelve a escribir entero.

Regla al tratar formatos ajenos: **lo que no entiendes, no lo toques, pero tampoco lo pierdas.** Lo mismo se aplica a los adjuntos de la cabecera interna, a los datos públicos de la cabecera y a los parámetros de la KDF.

## Leer sin perder información
El primer lector del diccionario de la KDF convertía los enteros de 32 bits, con o sin signo, en `Int`. Al escribir, ya no se sabía cuál de los dos tipos era el original. Ahora cada tipo del formato se lee como un tipo distinto de Kotlin (`UInt`, `ULong`, `Int`, `Long`, `Boolean`, `String`, `ByteArray`) y el diccionario se reescribe **byte a byte igual**.

Un lector pensado solo para leer puede descartar detalles. Un lector que alimenta a un escritor, no.

## Aleatoriedad segura
`java.util.Random` genera una secuencia que se puede reconstruir a partir de unos pocos valores: sirve para simulaciones, no para claves. `SecureRandom` es un generador criptográfico (**CSPRNG**), alimentado por el sistema operativo: conocer los valores anteriores no permite predecir los siguientes.

Todo lo que deba ser impredecible (claves, semillas, sales, vectores de inicialización) sale de `SecureRandom`.

## Qué se renueva en cada guardado
| Valor | Tamaño | Por qué |
|---|---|---|
| Semilla maestra | 32 bytes | Cambia las claves de cifrado y de HMAC |
| Vector de inicialización o *nonce* | 16 o 12 bytes | No se repite nunca con la misma clave |
| Clave del cifrado interno | 64 bytes | Flujo nuevo para las contraseñas del XML |
| Sal de la KDF | la del original | Como hace KeePass |

Con la semilla nueva cambian las claves, así que el *nonce* nunca coincide con una clave ya usada. Es la regla del [tema 14](14-el-xml-y-los-campos-protegidos.md): reutilizar clave y *nonce* en un cifrado de flujo revela el XOR de los dos textos.

Consecuencia: **guardar exige la contraseña**, porque hay que derivar las claves otra vez.

## El orden al escribir
Es el inverso de la lectura ([tema 13](13-autenticar-y-descifrar-el-contenido.md)):

1. Volver a cifrar los valores protegidos con la clave interna nueva, en el orden del documento.
2. Cabecera interna y XML.
3. Comprimir.
4. Cifrar.
5. Trocear en bloques, cada uno con su HMAC, y cerrar con el bloque vacío.
6. Delante: la cabecera, su SHA-256 y su HMAC.

Primero se cifra y después se autentica lo cifrado (*Encrypt-then-MAC*).

## Cómo se prueba un escritor
- **Ida y vuelta**: abrir, guardar y volver a abrir da exactamente el mismo contenido, elemento a elemento, incluido lo que AirPass no entiende.
- **Fidelidad**: el diccionario de la KDF se reescribe idéntico al original.
- **Aleatoriedad**: dos guardados del mismo contenido dan semilla, vector y sal distintos.
- **Bloques**: con bloques de 16 bytes en el test se comprueba que intercambiar o eliminar un bloque se detecta.
- **El oráculo**: `keepassxc-cli`, el propio KeePassXC, abre los ficheros que escribe AirPass y devuelve la entrada con su usuario y su contraseña. Que AirPass lea lo que AirPass escribe no demuestra nada si los dos comparten el mismo error.

## Cómo lo hace KeePass
En cada guardado genera una semilla maestra, un vector de inicialización y una clave interna nuevos, y renueva la semilla de la KDF. Conserva en memoria la clave compuesta, protegida, para no pedir la contraseña al guardar.

## Qué hace AirPass
- `abrirKdbx` devuelve una `BaseDeDatos`: el grupo raíz para mostrar y, por dentro, la cabecera, los adjuntos, el documento XML y todos los valores protegidos descifrados.
- `guardarKdbx(base, contrasena)` devuelve los bytes de un fichero KDBX completo, con el mismo cifrado, la misma KDF y la misma versión que el original.
- `bytesAleatorios` y `aes256CbcCifrar` en `crypto`.
- Los cuatro ficheros del corpus superan la ida y vuelta y se abren en KeePassXC.

**Pendiente**
- Comprobar a mano los ficheros en KeePass 2.62.
- Editar: añadir, modificar y borrar entradas.
- Escribir en disco de forma atómica (`SR-10`).
- Decidir cómo se obtiene la contraseña al guardar: pedirla otra vez o conservarla protegida.
- El XML que escribe Java no es idéntico carácter a carácter al de KeePass (espacios, declaración), aunque el contenido sí lo es.

## Preguntas de repaso
1. ¿Qué pasaría si AirPass guardara el fichero a partir solo de los grupos y entradas que entiende?
2. ¿Por qué hubo que cambiar el lector del diccionario para poder escribirlo?
3. ¿Por qué `java.util.Random` no sirve para generar una clave?
4. ¿Qué cuatro valores se renuevan al guardar y qué pasaría si no se renovara el *nonce*?
5. ¿Por qué no basta con que AirPass sea capaz de leer sus propios ficheros?
