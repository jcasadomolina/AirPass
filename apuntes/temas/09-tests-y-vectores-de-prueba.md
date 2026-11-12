# 09 · Tests y vectores de prueba

## Idea clave
Una función criptográfica mal implementada devuelve algo con el mismo aspecto aleatorio que una correcta. No se puede comprobar «a ojo»: hacen falta **vectores de prueba**, pares entrada/salida publicados por quien define el algoritmo.

## Anatomía de un test
Un test es una función normal marcada con `@Test`. Pasa si termina sin lanzar una excepción. Tiene tres partes:
1. **Preparar** una entrada.
2. **Ejecutar** lo que se prueba.
3. **Comprobar** el resultado con `assertEquals`, `assertTrue` o `assertFailsWith`.

Conviene escribir el test antes que el código y **verlo fallar**: así se sabe que de verdad comprueba algo.

## Vectores de prueba
| Algoritmo | Origen | Entrada | Uso en AirPass |
|---|---|---|---|
| SHA-256 | NIST, FIPS 180-4 | `abc` | `Sha256Test` |
| SHA-512 | NIST, FIPS 180-4 | `abc` | `PrimitivosTest` |
| HMAC-SHA-256 | RFC 4231, caso 2 | clave `Jefe` | `PrimitivosTest` |
| AES-256 | FIPS 197, apéndice C.3 | un bloque | `PrimitivosTest`, a través de AES-KDF |
| AES-256-CBC | NIST SP 800-38A, F.2.5 | un bloque | `PrimitivosTest` |
| ChaCha20 | RFC 8439, apartado 2.4.2 | un texto de 114 bytes | `PrimitivosTest` |

AES-KDF es propio de KeePass y no tiene vectores oficiales. Con una sola ronda equivale a cifrar dos bloques con AES-256, y eso sí tiene vector. Cuando un algoritmo no tiene vectores, se reduce a un caso que sí los tenga.

## El oráculo: un fichero real
Para el formato completo, el vector de prueba es un fichero creado con KeePass. El caso más fuerte hasta ahora: AirPass calcula el HMAC de la cabecera con su propio código y coincide byte a byte con el que escribió KeePass. Eso valida de una vez la clave compuesta, la KDF y la derivación de las claves.

La contraseña de un fichero de prueba es **pública a propósito** (está en los tests y en GitHub). Un fichero de prueba no debe contener nunca un dato real.

## Los casos malos importan más
Un fichero que se abre es una entrada que no controlo. En los tests de la cabecera hay un caso bueno y varios malos: firma incorrecta, fichero truncado, longitud imposible, algoritmo desconocido. En un lector de formatos, los fallos de seguridad casi nunca están en el camino feliz.

`assertFailsWith<IllegalArgumentException>` pasa solo si se lanza *esa* excepción. Si el código accede fuera del array sin comprobar el tamaño, la excepción es otra y el test falla: obliga a validar en lugar de romperse por accidente.

## Errores que detectaron los tests
Todos compilaban y tenían buen aspecto:
- Llamar a `digest()` sin pasarle los datos: devolvía siempre el hash de la entrada vacía, es decir, la misma clave para cualquier contraseña.
- Comparar un `Int` con una constante que no cabe en un `Int`: rechazaba todos los ficheros.
- Pasar `mayor` y `menor` en el orden contrario: devolvía la versión 0.4.

El tercero llegó a un commit porque el cambio se hizo después de pasar los tests. **Los tests se ejecutan justo antes de cada commit.**

## Preguntas de repaso
1. ¿Por qué no basta con mirar la salida de `sha256` para saber si está bien?
2. ¿Qué demuestra que el HMAC calculado por AirPass coincida con el del fichero de KeePass?
3. ¿Por qué el test del fichero truncado exige `IllegalArgumentException` y no acepta cualquier excepción?
