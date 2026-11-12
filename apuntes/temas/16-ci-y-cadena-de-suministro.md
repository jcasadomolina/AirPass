# 16 · Integración continua y cadena de suministro

## Idea clave
La integración continua ejecuta los tests por mí en una máquina limpia. Pero un flujo de CI **ejecuta código de terceros dentro de mi repositorio**, así que también es una superficie de ataque.

## Qué es la integración continua
Cada vez que se sube código, un servidor descarga el repositorio en una máquina recién creada, compila y pasa los tests. Si algo falla, el pull request lo muestra antes de fusionar.

Dos ventajas:
- No depende de que me acuerde de pasar los tests. En la sesión 5 se coló un commit con un test roto.
- La máquina limpia demuestra que el proyecto compila sin nada que solo exista en mi ordenador, como `local.properties`.

## Cadena de suministro
Mi aplicación no es solo mi código: incluye las dependencias, las herramientas que la compilan y las acciones de la CI. Un atacante que comprometa cualquiera de esos eslabones compromete el resultado sin tocar mi código. En 2025, una acción de GitHub muy usada fue alterada y filtró credenciales de miles de repositorios.

## Defensas en el flujo
| Defensa | Qué evita |
|---|---|
| Acciones fijadas por **hash de commit** | Una etiqueta como `v7` se puede mover a otro código; un hash, no |
| `permissions: contents: read` | Que una acción comprometida escriba en el repositorio |
| `persist-credentials: false` | Que el token de acceso quede al alcance de los pasos siguientes |
| Validación de `gradle-wrapper.jar` | Que un pull request sustituya ese binario, que nadie lee en la revisión |

```yaml
- uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
```

El comentario indica a qué versión corresponde el hash. Actualizar una acción es cambiar el hash a propósito, sabiendo qué versión se adopta.

## Principio de mínimo privilegio
Dar a cada pieza solo los permisos que necesita. El flujo únicamente lee código, así que no tiene permiso de escritura. Es la misma idea que `implementation` en Gradle ([tema 08](08-modulos-de-gradle.md)) y que tener claves separadas para cifrar y autenticar ([tema 11](11-de-la-contrasena-a-las-claves.md)).

## `SECURITY.md`
Es el fichero que GitHub muestra en la pestaña Security. Dice cómo avisar de una vulnerabilidad **en privado**: un fallo descrito en un *issue* público puede aprovecharse antes de estar corregido. Esto se llama **divulgación coordinada**.

## Qué hace AirPass
- `.github/workflows/ci.yml`: en Windows y Linux, tests de `crypto`, `core` y `shared` y la aplicación de escritorio; en Linux, además, el APK de depuración.
- Se ejecuta en cada pull request, en cada push a `main` y `develop`, y a mano.
- `SECURITY.md` con el aviso privado de GitHub, el alcance y la advertencia de que no hay ninguna versión publicada.
- `.gitattributes`: `gradlew` con finales de línea de Unix y marcado como ejecutable, y los `.kdbx` como binarios.

**Pendiente**
- El flujo **no se ha ejecutado nunca**: la cuenta de GitHub figura como bloqueada por un problema de facturación cuya causa no está aclarada. No es por la cuota, ni por un pago fallido, ni por el flujo. Hacer público el repositorio no lo resolvió.
- Cuando funcione, exigir que la CI pase antes de fusionar en `develop`.
- Escaneo de dependencias y SBOM (fase F5).

## Preguntas de repaso
1. ¿Qué aporta ejecutar los tests en una máquina limpia?
2. ¿Por qué `actions/checkout@v7` es menos seguro que el hash del commit?
3. ¿Por qué se valida `gradle-wrapper.jar`?
4. ¿Qué es el principio de mínimo privilegio? Pon dos ejemplos de AirPass.
5. ¿Por qué una vulnerabilidad no se comunica en un *issue* público?
