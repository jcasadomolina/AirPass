# 13 · Autenticar y descifrar el contenido

Este tema recorre el descifrado tal como está implementado. La teoría de *Encrypt-then-MAC* está en el [tema 04](04-cifrado-autenticado.md) y la obtención de las claves, en el [tema 11](11-de-la-contrasena-a-las-claves.md).

## Idea clave
Nunca se descifra nada que no se haya autenticado antes. El contenido va troceado en bloques, y cada bloque demuestra que es auténtico, que está completo y que ocupa su sitio.

## Qué hay después de la cabecera
```
cabecera │ SHA-256 │ HMAC │ bloque 0 │ bloque 1 │ … │ bloque final vacío
                            └── cada uno: HMAC (32) · longitud (4) · datos cifrados
```

## Qué autentica el HMAC de cada bloque
`HMAC-SHA-256(clave del bloque, índice ‖ longitud ‖ datos)`

| Qué se autentica | Qué impide |
|---|---|
| Los datos | Modificar el contenido |
| La longitud | Acortar o alargar un bloque |
| El índice (0, 1, 2…) | Reordenar, duplicar o eliminar bloques |

Cada bloque usa una clave distinta: `SHA-512(índice ‖ clave de HMAC)`. La cabecera usa el índice 2⁶⁴ − 1, que en un `Long` con signo es −1.

## El bloque final vacío
Si el fichero terminara sin más tras el último bloque, un atacante podría cortarlo justo entre dos bloques y todos los HMAC restantes seguirían siendo válidos. Por eso el contenido acaba siempre con un bloque de longitud 0, también autenticado. Si el lector no lo encuentra, el fichero está recortado.

Regla general: en un formato autenticado, **el final también tiene que estar autenticado**.

## AES en modo CBC
AES cifra bloques de 16 bytes. Usado tal cual (modo ECB), dos trozos iguales de texto dan el mismo cifrado y se ven los patrones. El modo **CBC** (*Cipher Block Chaining*) mezcla cada bloque con el cifrado del anterior antes de cifrarlo.

El **vector de inicialización** hace de «bloque anterior» para el primero. No es secreto, pero debe ser distinto e impredecible en cada cifrado: con el mismo vector y la misma clave, dos ficheros que empiezan igual tendrían el mismo comienzo cifrado.

Como los datos rara vez miden un múltiplo de 16, se añade un **relleno** (PKCS#7): si faltan *n* bytes, se añaden *n* bytes de valor *n*.

## Por qué el orden importa: *padding oracle*
Si un programa descifra antes de autenticar y responde distinto según el relleno sea válido o no, un atacante puede enviar miles de variantes y deducir el texto claro byte a byte, sin la clave. Verificando el HMAC primero, los datos manipulados nunca llegan al descifrado.

## Comprimir antes de cifrar
Lo cifrado parece aleatorio y no se puede comprimir, así que la compresión (GZip) se aplica antes. Al leer, el orden es el inverso: autenticar, descifrar y descomprimir.

Un riesgo de toda descompresión es la **bomba de compresión**: pocos KB que se expanden a gigas. Aquí el riesgo es bajo, porque el contenido está autenticado y solo podría prepararlo quien conoce la clave.

## La cabecera interna
Lo descifrado empieza con otra cabecera TLV, ya protegida por el cifrado:

| Tipo | Contenido |
|---|---|
| 1 | Cifrado interno: 2 = Salsa20, 3 = ChaCha20 |
| 2 | Clave del cifrado interno (64 bytes en los ficheros de KeePass) |
| 3 | Un adjunto |
| 0 | Fin |

Detrás va el XML. El cifrado interno se explica en el [tema 14](14-el-xml-y-los-campos-protegidos.md).

## Qué hace AirPass
- `leerBloquesAutenticados` comprueba el HMAC de cada bloque antes de usarlo y exige el bloque final vacío.
- `descifrarKdbx` autentica, descifra con `aes256CbcDescifrar`, descomprime y lee la cabecera interna. Devuelve el XML y la clave interna.
- Un bloque que no supera la verificación lanza `FicheroManipuladoException`. La contraseña ya se comprobó con la cabecera, así que aquí no es una clave incorrecta.
- Las longitudes de los bloques pasan por `leerBytes`, la misma validación que la cabecera.

**Pendiente**
- ~~ChaCha20 como cifrado externo~~: hecho, ver el [tema 17](17-argon2-y-la-primera-dependencia.md).
- Tope de tamaño al descomprimir.
- Los adjuntos se conservan al guardar, pero todavía no se muestran.
- ~~Probar el reordenado de bloques~~: hecho con bloques pequeños, ver el [tema 18](18-escribir-sin-perder-nada.md).

## Preguntas de repaso
1. ¿Qué ataque evita incluir el índice en el HMAC de cada bloque? ¿Y la longitud?
2. ¿Para qué sirve un bloque final que no contiene datos?
3. ¿Por qué el vector de inicialización no necesita ser secreto, pero sí distinto cada vez?
4. ¿Por qué se comprime antes de cifrar y no después?
5. ¿Qué es un *padding oracle* y por qué no afecta a KDBX 4?
