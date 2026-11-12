# Diario de desarrollo

## Sesión 1 · 2026-11-09: estudio de KeePass y planificación

**Qué se hizo**
1. Se instaló **REA** (herramienta de ingeniería inversa) y se conectó a Claude Code como servidor MCP. Antes de aplicar nada se revisó el plan con `--dry-run`. Hubo que actualizar Node.js a la 26.13.0 porque REA exige una versión mínima.
2. Se descargó **KeePass 2.62** (binario y código fuente) y se **comprobó su SHA-256** con el publicado en keepass.info → [tema 01](temas/01-integridad-de-descargas.md).
3. Se analizó `KeePass.exe` **sin ejecutarlo** con REA: tipos .NET, llamadas a APIs nativas de Windows y funciones exportadas por la DLL de Argon2 → [tema 02](temas/02-ingenieria-inversa-estatica.md).
4. Se contrastó el análisis con el código fuente para entender el formato **KDBX 4.1**: derivación de claves → [tema 03](temas/03-derivacion-de-claves.md); cifrado más HMAC → [tema 04](temas/04-cifrado-autenticado.md); protección de secretos en RAM → [tema 05](temas/05-secretos-en-memoria.md).
5. Se redactaron `docs/analisis-keepass.md`, `docs/seguridad.md` (requisitos `SR-01`…`SR-20`) y `docs/plan-desarrollo.md`, y se actualizó el `README`.
6. Se adoptó **gitflow**: `main` para producción, `develop` para desarrollo y `feature/*` para cada funcionalidad. La documentación entró por `feature/documentacion-inicial` con un merge `--no-ff` en `develop`.

**Decisiones tomadas**
- AirPass usará el formato **KDBX 4.1** para ser compatible con KeePass, en lugar de inventar un formato propio.
- Los apuntes van en `apuntes/` dentro del repositorio, organizados por temas más este diario.

**Decisiones pendientes**
- Stack (ADR-001). La recomendación es Kotlin Multiplatform, pero no está decidido.
- Librería KDBX (ADR-002): implementarla desde cero o usar una existente. Es una decisión importante: implementarla enseña mucho más, pero es más arriesgada.

**Lo que aprendí**
- Un gestor de contraseñas serio no protege solo «con cifrado». Combina un KDF lento, cifrado autenticado, protección en memoria y defensas frente al sistema operativo (portapapeles, capturas de pantalla, volcados de fallos).
- Ninguna defensa sirve contra malware que se ejecute con mis mismos privilegios. Ese límite hay que reconocerlo en el modelo de amenazas.

## Sesión 2 · 2026-11-09: decisiones de arquitectura

**Qué se hizo**
1. Se fijaron las reglas de trabajo del proyecto: yo tomo todas las decisiones y hago personalmente los commits, pushes y pull requests, siempre con título y una breve descripción.
2. Se redactó el **ADR-001** (stack) y se ajustó toda la documentación al alcance **Windows + Android** → [tema 06](temas/06-decisiones-de-arquitectura.md).
3. Se redactó el **ADR-002** (formato KDBX): implementación propia validada contra ficheros reales → [tema 07](temas/07-no-inventes-tu-criptografia.md).

**Decisiones tomadas**
- **ADR-001**: Kotlin Multiplatform con Compose Multiplatform para Windows (escritorio JVM) y Android. Se descartaron .NET MAUI, dos apps nativas y un núcleo en Rust.
- **ADR-002**: opción C. Implemento yo el formato KDBX sobre los primitivos de BouncyCastle y lo valido con un corpus de ficheros `.kdbx` creados con KeePass y KeePassXC. kotpass no entra en la app.

**Decisiones pendientes**
- ¿Leer KDBX 3.1 o solo 4.x? Propuesta: leer 3.1 y guardar siempre en 4.1.

**Lo que aprendí**
- Una decisión de arquitectura vale tanto como su **justificación** y sus **consecuencias** escritas. Las consecuencias negativas son la parte más útil de un ADR.
- El recolector de basura afecta igual a Kotlin y a .NET, así que no sirve para elegir entre ellos. Un criterio solo ayuda a decidir si distingue entre las opciones.
- Implementar un formato cifrado **no** es «inventar criptografía», siempre que los algoritmos vengan de una librería auditada y haya un oráculo externo que valide el resultado.

## Sesión 3 · 2026-11-09: esqueleto del proyecto

**Qué se hizo**
1. Generé el proyecto con el asistente de Kotlin Multiplatform (Android y escritorio) y lo copié a la raíz del repositorio. La plantilla ya viene dividida en tres módulos: `shared`, `androidApp` y `desktopApp`.
2. Hubo que definir `sdk.dir` en `local.properties` para que Gradle encontrara el SDK de Android. Es un fichero local que no se sube.
3. Hice por primera vez los commits divididos por temas, el push y el pull request desde la terminal → [tema 12](temas/12-git-en-el-dia-a-dia.md).

**Decisiones tomadas**
- Paquete base: `com.jcasadomolina.airpass`.
- Los commits, los pushes y los pull requests los hago yo. El asistente propone la división y redacta los títulos y las descripciones.

**Lo que aprendí**
- El aviso «LF will be replaced by CRLF» no es un error.
- Un commit con el título equivocado que ya está fusionado no se reescribe. Un `git log --oneline` antes del push lo habría detectado.

## Sesión 4 · 2026-11-10: módulos y primer código

**Qué se hizo**
1. Renombré `androidApp` y `desktopApp` a `app-android` y `app-desktop` → [tema 08](temas/08-modulos-de-gradle.md).
2. Creé el módulo `crypto` con la función `sha256` y su test con el vector del NIST → [tema 09](temas/09-tests-y-vectores-de-prueba.md).
3. Creé el módulo `core`, que depende de `crypto` con `implementation`.

**Decisiones tomadas**
- `crypto` y `core` son módulos Kotlin/JVM puros, no multiplataforma.
- El módulo `sync` se crea en la fase F4, cuando tenga contenido. Falta anotarlo en `docs/plan-desarrollo.md`.
- Forma de trabajo: los tests los escribe el asistente y me los explica.

**Decisiones pendientes**
- Comprobar si `shared` puede usar un módulo JVM puro desde `commonMain`. No afecta hasta la fase F2.

**Lo que aprendí**
- Mi primera versión de `sha256` llamaba a `digest()` sin pasarle los datos. Compilaba y devolvía 32 bytes con buen aspecto, pero habría dado la misma clave para cualquier contraseña. Solo el vector de prueba lo detecta.
- En Kotlin las funciones no tienen por qué ir dentro de una clase, y el paquete lo decide la línea `package`, no la carpeta.
- `implementation` hace que el compilador impida a la interfaz usar la criptografía directamente.

## Sesión 5 · 2026-11-11: cabecera y claves de KDBX

**Qué se hizo**
1. Creé con KeePass 2.62 el primer fichero del corpus, `keepass-aes-aeskdf.kdbx`, y miré sus bytes con `Format-Hex`.
2. Escribí `leerVersionKdbx`, que comprueba las firmas y la versión → [tema 10](temas/10-leer-un-formato-binario.md).
3. Lectura de los campos TLV de la cabecera con `leerCabeceraKdbx`. Este código y sus tests los escribió el asistente y me los explicó.
4. Derivación de las claves y verificación de la cabecera con `derivarClaves`: clave compuesta, AES-KDF, claves de cifrado y de HMAC → [tema 11](temas/11-de-la-contrasena-a-las-claves.md). También lo escribió el asistente. AirPass ya distingue la contraseña correcta de una incorrecta.

**Decisiones tomadas**
- AirPass acepta KDBX 4.0 y 4.1: KeePass 2.62 crea los ficheros nuevos en 4.0.
- Los ficheros de versión mayor distinta de 4 se rechazan con un error claro. Leer KDBX 3.1 sigue pendiente de decidir.

**Decisiones pendientes**
- Poner un tope al número de rondas de la KDF o permitir cancelar la apertura.
- Añadir BouncyCastle para Argon2, con un fichero de prueba de KeePassXC.

**Lo que aprendí**
- `0x9AA2D903` no cabe en un `Int`. Comparado con `compareTo` compilaba y rechazaba todos los ficheros.
- Un tamaño leído del fichero se valida antes de reservar memoria.
- El SHA-256 de la cabecera detecta daños; el HMAC detecta manipulaciones. Un atacante puede recalcular el primero, pero no el segundo.
- Una contraseña incorrecta y un fichero manipulado no se pueden distinguir.
- Commiteé un cambio hecho después de pasar los tests y dejé uno roto. Los tests se pasan justo antes de cada commit.

**Para repasar**
- Los pasos 3 y 4 no los escribí yo. Conviene releer `CabeceraKdbx.kt` y `ClavesKdbx.kt` con los temas 10 y 11 delante hasta poder explicar cada línea.


## Sesión 6 · 2026-11-12: del contenido cifrado a las entradas

**Qué se hizo**
1. Fusioné los pull requests de la cabecera, las claves y los apuntes de las sesiones 3 a 5.
2. Lectura de los bloques del contenido con verificación del HMAC de cada uno, descifrado AES-256-CBC, descompresión y cabecera interna, con `descifrarKdbx` → [tema 13](temas/13-autenticar-y-descifrar-el-contenido.md).
3. Lectura del XML y descifrado de los campos protegidos con ChaCha20, con `leerXmlKdbx` → [tema 14](temas/14-el-xml-y-los-campos-protegidos.md).
4. AirPass lee de principio a fin el fichero de prueba y obtiene el título, el usuario y la contraseña de la entrada creada en KeePass.

El código y los tests de los pasos 2 y 3 los escribió el asistente y me los explicó.

**Decisiones tomadas**
- Un bloque que no supera el HMAC es un error distinto de la contraseña incorrecta: `FicheroManipuladoException`.
- El XML se analiza con los DTD desactivados, aunque el contenido esté autenticado.
- Los valores protegidos se guardan como `ByteArray`.

**Decisiones pendientes**
- Cómo proteger en memoria las contraseñas ya descifradas (`SR-06`).
- Tope de tamaño al descomprimir.
- Crear ficheros de prueba con KeePassXC (Argon2 y ChaCha20) y uno con varios bloques.

**Lo que aprendí**
- El HMAC de cada bloque cubre el índice y la longitud además de los datos, y por eso no se pueden reordenar ni recortar bloques.
- Un formato autenticado necesita autenticar también dónde termina: para eso existe el bloque final vacío.
- Se autentica antes de descifrar. Hacerlo al revés abre la puerta al *padding oracle*.
- El cifrado interno usa un único flujo para todo el documento. Saltarse un valor protegido estropea los siguientes sin dar ningún error.
- Todos los primitivos usados hasta ahora están en el JDK. BouncyCastle solo hará falta para Argon2.

**Para repasar**
- Releer `ContenidoKdbx.kt` y `XmlKdbx.kt` con los temas 13 y 14 delante, y contestar sus preguntas de repaso.


## Sesión 7 · 2026-11-12: pantalla, integración continua y portapapeles

**Qué se hizo**
1. Primera pantalla de escritorio: elegir un fichero, escribir la contraseña y ver las entradas. En `core`, `abrirKdbx` y `borrarSecretos` → [tema 15](temas/15-la-interfaz-y-los-secretos.md).
2. Flujo de GitHub Actions, `SECURITY.md` y ajustes de `.gitattributes` → [tema 16](temas/16-ci-y-cadena-de-suministro.md).
3. Botón para copiar la contraseña, con exclusión del historial de Windows y borrado a los 12 segundos (RF-05) → [tema 15](temas/15-la-interfaz-y-los-secretos.md).
4. Probé a mano la pantalla y el portapapeles con el fichero de prueba.

El código de los tres pasos lo escribió el asistente y me lo explicó.

**Decisiones tomadas**
- La pantalla vive de momento en `app-desktop`, no en `shared`.
- Las contraseñas de las entradas no se muestran: solo se copian.
- Plazo de borrado del portapapeles: 12 segundos, como KeePass.
- Hice público el repositorio.

**Decisiones pendientes**
- La integración continua no se ha ejecutado nunca: GitHub indica que la cuenta está bloqueada por un problema de facturación. No hay pagos fallidos ni cuota agotada. Queda aparcado; el siguiente paso sería preguntar a soporte.
- Sustituir el campo de contraseña de Compose por uno que no use `String`.
- Revisar el historial del repositorio ahora que es público.

**Lo que aprendí**
- La limpieza de las claves va en `core`, con `try`/`finally`, para que no dependa de cada pantalla.
- Una operación lenta fuera del hilo de la interfaz, o la ventana se congela.
- En Windows, borrar el portapapeles no basta: existe el historial. Hay formatos para pedir que no se guarde.
- Una etiqueta de versión se puede mover; un hash de commit, no.
- Un fichero que git ya sigue no deja de seguirse por añadirlo después al `.gitignore`: hay que sacarlo con `git rm --cached`.
- Guardar los códigos de recuperación de la cuenta de GitHub.

**Para repasar**
- Releer `Escritorio.kt` y `Portapapeles.kt` con el tema 15 delante, y `ci.yml` con el tema 16.
