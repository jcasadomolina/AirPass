# 17 · Argon2 y la primera dependencia

La teoría general de las KDF está en el [tema 03](03-derivacion-de-claves.md) y la cadena de claves de KDBX, en el [tema 11](11-de-la-contrasena-a-las-claves.md).

## Idea clave
Una KDF que solo gasta tiempo de procesador se abarata con hardware en paralelo. Argon2 gasta además **memoria**, que no se paraleliza igual de barato. Y sus parámetros son el único dato del fichero en el que hay que confiar antes de poder comprobar nada.

## Por qué la memoria
Un atacante prueba contraseñas en tarjetas gráficas o en chips a medida, con miles de núcleos trabajando a la vez. AES-KDF solo pide cálculo, así que cada núcleo prueba una contraseña por su cuenta.

Argon2 obliga a rellenar y recorrer un bloque grande de memoria en cada intento, por ejemplo 64 MiB. Mil intentos en paralelo necesitan 64 GiB. El atacante deja de estar limitado por los núcleos y pasa a estarlo por la memoria, que es cara. A esto se le llama una función **dura en memoria** (*memory-hard*).

## Los tres parámetros
| Parámetro | Qué controla |
|---|---|
| Memoria | Cuánta RAM exige cada intento. Es el que más encarece el ataque |
| Iteraciones | Cuántas pasadas se dan sobre esa memoria |
| Paralelismo | Cuántos hilos usa el cálculo legítimo |

## Las variantes
| Variante | Cómo accede a la memoria | Punto fuerte |
|---|---|---|
| Argon2d | Según los datos | Máxima resistencia a hardware especializado |
| Argon2i | De forma independiente de los datos | Resistencia a ataques de canal lateral |
| Argon2id | Mezcla de las dos | Equilibrio. Es la recomendada por la RFC 9106 |

Un **canal lateral** es información que se escapa por cómo se ejecuta un cálculo y no por su resultado: lo que tarda, qué posiciones de memoria toca. Si los accesos dependen del secreto, quien pueda observarlos aprende algo de él.

## Parámetros no autenticados
Para comprobar el HMAC de la cabecera hace falta la clave, y la clave sale de la KDF. Por tanto, los parámetros de la KDF se usan **antes** de poder verificar que la cabecera es auténtica. Un fichero manipulado puede pedir 64 GiB de memoria y tumbar la aplicación sin conocer la contraseña.

Regla general: **lo que se usa antes de autenticar es entrada no fiable, y se le ponen límites**. Es el mismo principio que validar las longitudes del [tema 10](10-leer-un-formato-binario.md).

El ataque inverso, rebajar los parámetros para facilitar la fuerza bruta, no funciona en KDBX 4: con parámetros distintos sale otra clave y el HMAC de la cabecera no coincide.

## Añadir una dependencia
Hasta este paso todo salía del JDK. Argon2 no está en el JDK, así que entra BouncyCastle. Cada dependencia es código ajeno que se ejecuta con los mismos permisos que el mío:

- **Solo donde hace falta.** BouncyCastle está en `crypto` con `implementation`; ni `core` ni la interfaz pueden usarlo.
- **No la versión recién publicada.** Se eligió la 1.86, con dos meses de uso, y no la 1.87, publicada el día anterior. Una versión comprometida o defectuosa suele descubrirse en sus primeros días.
- **Versión fija** en el catálogo, sin rangos: actualizar es una decisión, no algo que ocurre solo.

## Cómo lo hace KeePass
KeePass 2.x usa AES-KDF por defecto y ofrece Argon2d y Argon2id. KeePassXC usa Argon2 por defecto. En el diccionario de parámetros: `S` la sal, `M` la memoria en bytes, `I` las iteraciones, `P` el paralelismo y `V` la versión (`0x13`).

## Qué hace AirPass
- `argon2` en `crypto`, sobre BouncyCastle, validada con los vectores de la RFC 9106 para Argon2d y Argon2id.
- `transformarClave` elige entre AES-KDF, Argon2d y Argon2id según el identificador del fichero.
- Límites antes de derivar: 1 GiB de memoria, 100 iteraciones y 64 hilos.
- ChaCha20 como cifrado externo: mismo `FlujoChaCha20` que el cifrado interno, con la clave de cifrado y el *nonce* de 12 bytes de la cabecera.
- El corpus tiene cuatro ficheros, y un test los abre todos:

| Fichero | Programa | Cifrado | KDF |
|---|---|---|---|
| `keepass-aes-aeskdf.kdbx` | KeePass 2.62 | AES-256 | AES-KDF |
| `keepassxc-aes-argon2d.kdbx` | KeePassXC | AES-256 | Argon2d |
| `keepassxc-aes-argon2id.kdbx` | KeePassXC | AES-256 | Argon2id |
| `keepassxc-chacha20-argon2id.kdbx` | KeePassXC | ChaCha20 | Argon2id |

**Pendiente**
- Argon2 en Java puro es lento: abrir cada fichero de KeePassXC tarda del orden de segundos. El plan ya lo recoge como riesgo para los móviles de gama baja.
- AES-KDF sigue sin tope de rondas.
- Verificación de dependencias de Gradle, para comprobar el hash de BouncyCastle en cada compilación.
- Ficheros manipulados en el corpus y un fichero de varios bloques.

## Preguntas de repaso
1. ¿Por qué exigir memoria perjudica más al atacante que al usuario legítimo?
2. ¿Qué diferencia a Argon2d de Argon2id?
3. ¿Por qué los parámetros de la KDF no se pueden autenticar antes de usarlos?
4. ¿Por qué un atacante no gana nada rebajando las iteraciones en la cabecera?
5. ¿Por qué no se eligió la última versión de BouncyCastle?
