# 05 · Secretos en memoria

## Idea clave
Mientras la base de datos está abierta, los secretos están en la **RAM**. Desde ahí pueden acabar en sitios persistentes: el fichero de paginación, el de hibernación, volcados de fallos o un volcado hecho por otro proceso. Cifrar el fichero no protege lo que hay en memoria.

## Defensas de KeePass
- **`ProtectedString` / `ProtectedBinary`**: los valores sensibles se guardan **cifrados en RAM**. Usan DPAPI en Windows y, si no está disponible, ChaCha20 con una clave del proceso. Se descifran justo antes de usarlos.
- **Borrado explícito**: los búferes temporales se sobrescriben con ceros (`MemUtil.ZeroByteArray`) en cuanto dejan de necesitarse.
- **Exclusión de los informes de errores de Windows** (`WerAddExcludedApplication`): si el programa falla, no se envía un volcado con secretos.
- **DACL del proceso**: limita qué otros procesos pueden abrir el de KeePass para leer su memoria.

## El problema de los lenguajes con recolector de basura (GC)
- En .NET y en la JVM, un `String` es **inmutable**: no se puede sobrescribir, y queda en memoria hasta que el GC lo reutilice, sin saber cuándo.
- El GC **mueve** objetos al compactar la memoria y puede dejar copias antiguas.

**Caso real, CVE-2023-32784 (KeePass < 2.54)**: el cuadro de texto de la contraseña creaba una cadena residual por cada carácter escrito («•a», «••b», …). A partir de un volcado de memoria se podía reconstruir la contraseña maestra, salvo el primer carácter. Se corrigió en 2.54.

## En AirPass (Kotlin/JVM y Android ART)
- `SR-06`: la contraseña maestra **nunca** se guarda en un `String`. Se usa `CharArray`/`ByteArray`, que se rellenan con ceros (`fill(0)`) después de usarlos.
- Hay que mantener descifrado el menor tiempo posible y proteger por defecto también las notas.
- Verificación práctica: hacer un volcado de memoria del proceso y buscar en él la contraseña de prueba (en la fase F2).
- Android: `FLAG_SECURE` (sin capturas de pantalla), sin copias de seguridad automáticas y teclado sin aprendizaje.

## Preguntas de repaso
1. ¿Por qué cifrar el fichero `.kdbx` no protege frente a un volcado de memoria?
2. ¿Por qué un `CharArray` es mejor que un `String` para una contraseña en Kotlin?
3. ¿Qué tienen en común el fichero de paginación y un volcado de fallo desde el punto de vista de un atacante?
