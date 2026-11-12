# 08 · Módulos de Gradle

## Idea clave
Un proyecto se divide en **módulos** para que cada parte tenga una sola responsabilidad y solo vea lo que necesita. En AirPass, además, la división es una medida de seguridad: el compilador impide que la interfaz toque la criptografía directamente.

## Qué es un módulo
Para Gradle, un módulo (o *subproyecto*) es una carpeta que cumple dos condiciones:
1. Contiene un `build.gradle.kts`.
2. Está declarada en `settings.gradle.kts` con `include(":nombre")`.

Si falta una de las dos, no funciona: una carpeta sin declarar no existe para Gradle y una declaración sin carpeta da error. Los dos puntos indican la ruta desde la raíz, y ese mismo nombre se usa en la terminal: `./gradlew :crypto:test`.

## Plugins frente a dependencias
- Un **plugin** enseña a Gradle a *hacer* algo: compilar Kotlin, empaquetar una app Android. Va en `plugins { }`.
- Una **dependencia** es una biblioteca que *mi código usa*. Va en `dependencies { }`.

Las versiones de ambos están en un único sitio, el **catálogo** `gradle/libs.versions.toml`. Lo que está bajo `[plugins]` se usa como `libs.plugins.xxx` y lo que está bajo `[libraries]`, como `libs.xxx`.

## `implementation`, `api` y `testImplementation`
| Declaración | Quién ve la dependencia |
|---|---|
| `implementation(...)` | Solo este módulo |
| `api(...)` | Este módulo y todos los que dependan de él |
| `testImplementation(...)` | Solo los tests de este módulo; no se empaqueta en la app |

## Código de producción y código de pruebas
En un módulo JVM, `src/main/kotlin` es lo que acaba en la app y `src/test/kotlin` solo existe al ejecutar los tests. Los tests ven el código de `main`, pero no al revés. Un fichero colocado por error en `src/test` hace pasar los tests y deja vacío el `.jar` del módulo.

En Kotlin, el paquete lo decide la línea `package`, no la carpeta. Si falta, la función queda en el paquete por defecto y el resto del código no la encuentra, aunque el fichero esté en su sitio.

## Qué hace AirPass
- Módulos actuales: `app-android`, `app-desktop`, `shared`, `core` y `crypto`. El módulo `sync` se creará en la fase F4.
- `crypto` y `core` son módulos **Kotlin/JVM puros**: Android ejecuta bytecode de JVM, así que un `.jar` sirve para las dos plataformas de la v1 y la configuración es mínima.
- `crypto` no depende de ningún otro módulo ni de bibliotecas externas (solo `kotlin-test` para las pruebas). Cuantas menos dependencias tenga el módulo criptográfico, menos código de terceros puede tocar las claves.
- `core` depende de `crypto` con **`implementation`**. Así, la interfaz solo puede pedir a `core` operaciones completas («abre esta base de datos») y no puede llamar a `sha256` o `aesKdf` por su cuenta.

## Preguntas de repaso
1. ¿Qué dos cosas necesita una carpeta para ser un módulo de Gradle?
2. Si `core` declara `crypto` con `implementation`, ¿puede `app-desktop` llamar a `sha256` dependiendo solo de `core`? ¿Y si fuera `api`?
3. ¿Por qué `crypto` no debe depender de `shared`?
