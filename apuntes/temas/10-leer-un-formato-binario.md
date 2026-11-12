# 10 · Leer un formato binario

## Idea clave
Un fichero es una **entrada que no controlo**: puede estar dañado o haberlo preparado un atacante. Todo lo que el fichero dice sobre sí mismo (su tipo, su versión, sus longitudes) se comprueba antes de usarlo.

## La firma (*magic number*)
Los primeros bytes de muchos formatos son un valor fijo que los identifica. Permite rechazar de inmediato una foto o un PDF antes de pedir la contraseña o de gastar tiempo derivando la clave.

## *Little-endian*
Un número de varios bytes se puede guardar empezando por el byte más significativo (*big-endian*) o por el menos significativo (*little-endian*). `0x9AA2D903` en *little-endian* se escribe `03 D9 A2 9A`. Si se lee en el orden equivocado, sale otro número y ningún aviso.

En Kotlin y Java: `ByteBuffer.wrap(datos).order(ByteOrder.LITTLE_ENDIAN)` y después `getInt()`, `getShort()` o `getLong()`, que avanzan solos.

## El signo de los enteros
`Int` tiene signo y su máximo es 2 147 483 647. `0x9AA2D903` vale 2 594 363 651: no cabe, así que Kotlin lo trata como `Long`. Los mismos 32 bits leídos con `getInt()` dan −1 700 603 645. Para comparar hay que escribir `0x9AA2D903.toInt()`, que conserva los bits.

`==` avisa en compilación de que los tipos no coinciden. `compareTo` acepta comparar `Int` con `Long` y oculta el error.

## TLV: tipo, longitud, valor
Una forma habitual de guardar campos: un **tipo** que dice qué es, una **longitud** que dice cuánto ocupa y el **valor**. Permite saltarse los campos que no se conocen y añadir campos nuevos sin romper los lectores antiguos.

## La regla más importante
**Nunca usar un tamaño que venga del fichero sin compararlo antes con lo que queda por leer.**

Si un campo declara 2 GB y el código hace `ByteArray(longitud)` sin comprobar, la app se queda sin memoria antes de pedir la contraseña. Con una longitud negativa, falla de otra manera. En lenguajes sin comprobación de límites (C, C++) este mismo error permite leer o escribir memoria ajena.

## Cómo lo hace KDBX 4
| Bytes | Contenido |
|---|---|
| 0–3 | Firma 1: `0x9AA2D903` |
| 4–7 | Firma 2: `0xB54BFB67` |
| 8–9 | Versión menor |
| 10–11 | Versión mayor |
| 12… | Campos TLV: tipo (1 byte), longitud (4 bytes) y valor, hasta el tipo 0 |

Campos de la cabecera: 2 cifrado (16 bytes), 3 compresión (4), 4 semilla maestra (32), 7 vector de inicialización (16 con AES, 12 con ChaCha20) y 11 parámetros de la KDF. Ninguno es secreto: la semilla y el vector no protegen por estar ocultos, sino por ser distintos en cada fichero.

KeePass 2.62 no escribe siempre la versión 4.1: elige la más baja que le basta. Un fichero recién creado sale en **4.0**.

Los parámetros de la KDF usan otro formato dentro del campo, el *VariantDictionary*: pares nombre-valor con un byte de tipo cada uno.

## Qué hace AirPass
- `leerVersionKdbx` comprueba el tamaño mínimo, las dos firmas y que la versión mayor sea 4. Acepta 4.0 y 4.1.
- `leerCabeceraKdbx` recorre los campos y comprueba que los obligatorios existen y miden lo que deben. Rechaza los algoritmos desconocidos en lugar de intentar descifrar con el equivocado.
- `leerBytes` es la única función que reserva memoria a partir de una longitud del fichero, y siempre la valida antes. La usan la cabecera y el diccionario.
- Devuelve también cuánto mide la cabecera, porque su integridad se calcula sobre esos bytes exactos.
- Si un campo aparece dos veces, se usa el último, como hace KeePass.

## Preguntas de repaso
1. ¿Qué bytes hay en el fichero para el número `0x00000010` en *little-endian*?
2. ¿Por qué `firma == 0x9AA2D903` no compila si `firma` es un `Int`?
3. ¿Qué puede pasar si se reserva memoria con una longitud leída del fichero sin validarla?
4. La firma y la versión no están cifradas. ¿Es un problema?
