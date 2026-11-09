# ADR-002: Implementación del formato KDBX

- **Estado:** Propuesto
- **Fecha:** 2026-11-09

## Contexto

AirPass guarda las contraseñas en ficheros **KDBX 4.1**, el formato de KeePass ([ADR-001](001-stack.md), `SR-01`). Todo depende de leer y escribir ese fichero correctamente: un error puede **exponer secretos** (cifrado o claves mal construidos) o **destruir datos** (un fichero que no se vuelve a abrir).

Lo que hay que implementar ([analisis-keepass.md §3](../analisis-keepass.md)):
- Cabecera externa en TLV, autenticada con SHA-256 y HMAC-SHA-256.
- Derivación de claves: clave compuesta → AES-KDF o Argon2d/id → clave de cifrado y clave HMAC.
- Carga útil en `HmacBlockStream`, cifrada con AES-256-CBC o ChaCha20 y con GZip opcional.
- Cabecera interna (cifrado interno ChaCha20 y adjuntos) y el XML con grupos, entradas, historial y `DeletedObjects`.

Además, AirPass es un proyecto de aprendizaje y de portfolio centrado en la seguridad ([plan](../plan-desarrollo.md)). Entender el formato es uno de sus objetivos.

## Opciones consideradas

### A. Usar una librería KDBX existente (kotpass)
`keemobile/kotpass`: Kotlin/JVM, licencia MIT, unas 40 estrellas y último cambio en septiembre de 2025.
- ✅ Funcionalidad disponible en poco tiempo.
- ❌ El núcleo crítico dependería de un proyecto pequeño y con poca actividad. Es un riesgo de **cadena de suministro** y obligaría a auditarlo de todos modos.
- ❌ No controlamos cómo maneja los secretos en memoria (`SR-06`).
- ❌ Casi no aporta aprendizaje sobre el formato.

### B. Implementación propia sin referencia externa
- ✅ Máximo aprendizaje y control total.
- ❌ Sin un juez externo, los errores de interpretación de la especificación pasan desapercibidos hasta que un usuario pierde datos.

### C. Implementación propia validada contra ficheros reales
Se implementa el formato sobre los **primitivos de BouncyCastle** y se valida cada paso contra ficheros `.kdbx` creados por KeePass y KeePassXC.
- ✅ Aprendizaje y control de la opción B.
- ✅ Los ficheros reales actúan como **oráculo de pruebas**: si AirPass los abre y produce ficheros que KeePass abre, la implementación es correcta.
- ❌ Es más trabajo que la opción A y exige preparar y mantener un corpus de ficheros.

## Decisión

Se adopta la **opción C**: implementación propia del formato KDBX en el módulo `core`, usando BouncyCastle solo para los primitivos y validada contra un corpus de ficheros reales.

kotpass **no** se incluye en la aplicación. Si hace falta una segunda opinión, puede usarse solo en las pruebas.

## Justificación

- **No es criptografía propia**: los algoritmos (AES, ChaCha20, Argon2, HMAC, SHA-2) los aporta BouncyCastle. Lo que se implementa es cómo se **componen** según una especificación pública. Ahí es donde están el aprendizaje y el control, y el corpus de pruebas mantiene el riesgo acotado.
- Evita depender de un proyecto pequeño para la parte más sensible de la aplicación.
- Permite aplicar desde el principio las reglas de memoria (`SR-06`) y de rechazo estricto (`SR-05`).

## Consecuencias

**Corpus de pruebas obligatorio** (`core/src/test/resources/kdbx/`), creado con KeePass 2.62 y KeePassXC. Debe cubrir:

| Dimensión | Variantes |
|---|---|
| KDF | AES-KDF · Argon2d · Argon2id |
| Cifrado | AES-256 · ChaCha20 |
| Clave | Contraseña · contraseña + fichero clave · solo fichero clave |
| Compresión | GZip · sin compresión |
| Contenido | Vacío · entradas con historial, adjuntos y campos protegidos · elementos borrados |
| Manipulados | Cabecera modificada · bloque alterado · fichero truncado → deben **rechazarse** |

Cada fichero guarda junto a él la contraseña de prueba y el contenido esperado en claro, para comparar el resultado.

**Orden de implementación**, de menos a más acoplado, y cada paso con sus pruebas:
1. Lectura de la cabecera externa (sin descifrar).
2. Clave compuesta y KDF, verificados con los vectores de los RFC y el corpus.
3. Verificación del HMAC de la cabecera y de `HmacBlockStream`.
4. Descifrado, descompresión y cabecera interna.
5. Análisis del XML y modelo de dominio.
6. Escritura y prueba de ida y vuelta: AirPass → KeePass → AirPass.

**Riesgos asumidos**
- Lleva más tiempo hasta tener una base de datos utilizable. Se acepta, porque es el objetivo de aprendizaje.
- Hay que leer la especificación con cuidado y documentar cada interpretación dudosa en los apuntes.
- No se copia código de KeePass (GPLv2). El análisis ya hecho sirve como referencia de comportamiento, pero el código se escribe a partir de la especificación y la documentación pública del formato.

**Pendiente de decidir**: si AirPass **leerá KDBX 3.1** (ficheros antiguos) o solo 4.x. La propuesta es leer 3.1 y guardar siempre en 4.1, pero puede dejarse para después de la v1.
