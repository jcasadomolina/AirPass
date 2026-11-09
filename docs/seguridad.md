# Seguridad: qué hace KeePass y qué debe hacer AirPass

Complementa a [analisis-keepass.md](analisis-keepass.md). Las marcas **[Obs]**, **[Inf]** y **[?]** tienen el mismo significado que allí. Los identificadores `ev_…` remiten a la Evidence de REA.

## 1. Modelo de amenazas de KeePass

| Amenaza | ¿Protege? | Cómo |
|---|---|---|
| Robo del fichero `.kdbx` (portátil, copia en la nube o USB) | Sí | Cifrado autenticado y KDF lento |
| Manipulación del fichero (bits cambiados, cabecera alterada) | Sí (KDBX 4) | HMAC-SHA-256 sobre la cabecera y sobre cada bloque |
| Fuerza bruta offline de la contraseña maestra | Parcialmente | Depende del KDF y de la fortaleza de la contraseña |
| Volcado de memoria del proceso o del fichero de paginación | Parcialmente | `ProtectedString`/`ProtectedBinary` y exclusión de WER |
| Keylogger o captura de pantalla de otro proceso del usuario | Parcialmente | Escritorio seguro y `SetWindowDisplayAffinity` |
| Malware con los mismos privilegios que el usuario | **No** | Fuera de alcance. Puede leer la memoria, inyectar un plugin o reescribir la configuración |
| Pérdida de datos al guardar (corte de luz, disco lleno) | Sí | Escritura transaccional y atómica |

AirPass debe asumir el mismo límite: **un dispositivo comprometido con privilegios del usuario no es defendible**. La sincronización añade una superficie nueva, la red local, que KeePass no tiene (§6).

## 2. Criptografía en reposo [Obs código fuente]

- **Construcción**: Encrypt-then-MAC. Usa AES-256-CBC o ChaCha20 para cifrar y HMAC-SHA-256 por bloques (`HmacBlockStream`) para autenticar. El MAC cubre el índice del bloque, de modo que no se pueden reordenar, truncar ni duplicar bloques sin que se detecte.
- **Separación de claves**: la clave de cifrado se calcula con SHA-256 y la de HMAC con SHA-512 más un sufijo `0x01`. Ambas parten de `MasterSeed`, aleatoria de 32 bytes y nueva en cada guardado, y de la clave transformada. Como la semilla cambia en cada guardado, la clave efectiva también cambia aunque la contraseña sea la misma.
- **KDF**:
  - AES-KDF (600 000 rondas) no consume memoria, por lo que es barato de atacar con GPU o ASIC.
  - Argon2d/id con 64 MiB por defecto es mucho más resistente.
  - Argon2d no es resistente a canales laterales, mientras que Argon2id sí lo es. Al ser un proceso local, el riesgo es bajo, pero Argon2id es la elección actual recomendada (RFC 9106).
- **KDBX 3.1 frente a 4**: la versión 3.1 no tenía HMAC de cabecera, solo `StreamStartBytes` y `HashedBlockStream` (SHA-256 sin clave). Un atacante podía, por ejemplo, rebajar las rondas del KDF. KDBX 4 lo corrige.
- **Aleatoriedad**: `CryptoRandom` combina el CSPRNG del sistema con entropía adicional (contadores y entropía aportada por el usuario).
- **Autotest**: `SelfTest` valida AES, ChaCha20, Argon2, HMAC y otros con vectores conocidos antes de usarlos.

## 3. Secretos en memoria [Obs]

- `ProtectedString` y `ProtectedBinary` guardan los valores cifrados en RAM. En Windows usan DPAPI `ProtectedMemory` con `SameProcess` y, si no está disponible (Mono), ChaCha20 con una clave aleatoria del proceso. Solo descifran justo antes de usar el valor y luego ponen a cero los búferes temporales (`MemUtil.ZeroByteArray`).
- Por defecto solo se protege el campo `Password`. Título, usuario, URL y notas quedan en claro en memoria salvo que se configure lo contrario (`PwDefs.cs`: `ProtectPassword = true` y el resto `false`).
- **Límite inherente a un runtime con GC**: el recolector de basura puede mover objetos y dejar copias, y los `string` inmutables de .NET no se pueden borrar.
  - **Caso real, CVE-2023-32784 (KeePass < 2.54)**: el cuadro de contraseña (`SecureTextBoxEx`) dejaba en memoria cadenas residuales que permitían reconstruir la contraseña maestra, salvo el primer carácter, a partir de un volcado del proceso, del fichero de paginación o del de hibernación. Se corrigió en 2.54.
  - **Lección para AirPass**: nunca materializar la contraseña como `String`. Usar `CharArray`/`ByteArray` que se borren tras usarse y validar con análisis de volcados de memoria.

## 4. Endurecimiento frente al sistema operativo [Obs REA, `ev_8a0003b0…`]

REA enumera las APIs nativas que declara `KeePass.exe`. Cada una corresponde a una defensa concreta:

| API declarada | Defensa |
|---|---|
| `CreateDesktop`, `SwitchDesktop`, `SetThreadDesktop`, `OpenInputDesktop` | **Escritorio seguro** para introducir la clave maestra, de forma que otros procesos del escritorio normal no capturen las pulsaciones |
| `SetWindowDisplayAffinity` | Impide que la ventana aparezca en capturas de pantalla o grabaciones |
| `WerAddExcludedApplication` | Excluye el proceso de Windows Error Reporting, para que un fallo no envíe un volcado con secretos |
| `KeePassLibN!AuxProtectProcessWithDacl`, `OpenProcessToken`, `GetTokenInformation` | DACL restrictiva sobre el proceso, que dificulta que otros procesos lean su memoria (opción `ProtectProcessWithDacl`) |
| `ImmDisableIME` | Desactiva el IME en los campos de contraseña, porque podría registrar lo que se escribe |
| `OpenClipboard`, `SetClipboardData`, `RegisterClipboardFormat`, `SetClipboardViewer` | Portapapeles: borrado a los 12 s, exclusión del historial y de la sincronización en la nube del portapapeles de Windows (`ClipboardNoPersist`) y marcado para que otros visores lo ignoren |
| `SendInput`, `BlockInput`, `GetForegroundWindow`, `ToUnicodeEx`, `VkKeyScanEx` | Auto-Type: bloquea la entrada del usuario mientras escribe y comprueba la ventana de destino. La ofuscación en dos canales (portapapeles más teclado) es opcional |
| `RegisterHotKey` | Tecla rápida global de Auto-Type |
| `GetLastInputInfo` y `SessionLockNotifier` | Bloqueo por inactividad (`LockAfterTime`) y al bloquear la sesión de Windows |
| `KtmW32!CreateTransaction/CommitTransaction`, `MoveFileTransacted`, `MoveFileEx` | Guardado atómico que evita dejar ficheros `.kdbx` corruptos |
| `ShutdownBlockReasonCreate` | Evita perder cambios sin guardar al apagar el equipo |
| `SetDllDirectory` | Reduce el riesgo de *DLL hijacking* al cargar las DLL nativas |

Además, `KeePassLibN.x64.dll` se compila con Control Flow Guard [Obs `ev_1c4ae7c5…`].

## 5. Superficies de ataque conocidas en KeePass

- **Plugins**: se ejecutan con plena confianza dentro del proceso, y los `.plgx` se compilan en el propio equipo. Un plugin malicioso lo controla todo.
- **Disparadores (ECAS) y configuración**: quien pueda escribir en `KeePass.config.xml` puede añadir un disparador que exporte la base de datos en claro al abrirla. Así funciona el CVE-2023-24055, discutido por el autor porque exige acceso de escritura previo. KeePass ofrece una configuración forzada (`KeePass.config.enforced.xml`) en una ruta protegida como mitigación.
- **Auto-Type** puede escribir en la ventana equivocada si el foco cambia.
- **Almacenamiento remoto por URL** (FTP/HTTP/WebDAV): la seguridad depende del transporte que configure el usuario.
- **Comprobación de actualizaciones**: es la única conexión de red por defecto. Revela que el usuario tiene KeePass.

## 6. Requisitos de seguridad para AirPass

AirPass añade dos cosas que KeePass no tiene: **aplicación móvil** y **sincronización local entre dispositivos**. Los requisitos se identifican como `SR-xx` para trazarlos en el plan.

### Cifrado y formato
- **SR-01** Compatibilidad con **KDBX 4.1**: AirPass abre y guarda ficheros que KeePass puede leer y viceversa. No se inventa un formato propio.
- **SR-02** KDF por defecto **Argon2id**, calibrado en cada dispositivo para tardar unos 0,5–1 s en el más lento del usuario, con al menos 64 MiB. AES-KDF solo para leer ficheros existentes.
- **SR-03** Usar librerías auditadas (libsodium o BouncyCastle) para los primitivos. Nunca criptografía propia. Incluir vectores de prueba oficiales como autotest (equivalente a `SelfTest`).
- **SR-04** `MasterSeed`, IV y claves internas nuevos en cada guardado, generados con el CSPRNG del sistema.
- **SR-05** Rechazar ficheros con HMAC inválido sin intentar recuperar datos parciales.

### Memoria y plataforma
- **SR-06** No convertir nunca la contraseña maestra ni los secretos en `String`. Usar búferes que se borren tras usarse y mantener los campos protegidos cifrados en memoria. Proteger por defecto también las notas y los campos personalizados marcados como sensibles.
- **SR-07** Desbloqueo rápido con biometría: guardar la clave derivada envuelta con una clave protegida por la plataforma. En Android, una clave del Keystore respaldada por hardware, con `setUserAuthenticationRequired(true)` y que se invalide al cambiar la biometría. En Windows, Windows Hello o DPAPI con ámbito de usuario. La contraseña maestra sigue siendo obligatoria tras reiniciar o tras N días.
- **SR-08** Equivalentes de las defensas de KeePass en cada plataforma.

  En Windows:
  - `SetWindowDisplayAffinity` para excluir la ventana de capturas de pantalla.
  - Portapapeles con los formatos `ExcludeClipboardContentFromMonitorProcessing`, `CanIncludeInClipboardHistory=0` y `CanUploadToCloudClipboard=0`, más borrado automático.
  - Exclusión de Windows Error Reporting.

  En Android:
  - `FLAG_SECURE` y la vista de privacidad en multitarea, en lugar de `SetWindowDisplayAffinity`.
  - El framework de **Autofill** en lugar de Auto-Type.
  - Portapapeles marcado como sensible (`ClipDescription.EXTRA_IS_SENSITIVE`) y borrado automático.
  - `android:allowBackup="false"`, sin volcados de fallos que contengan secretos y teclado sin aprendizaje (`IME_FLAG_NO_PERSONALIZED_LEARNING`).
- **SR-09** Bloqueo automático por inactividad, al pasar a segundo plano y al bloquear la pantalla.
- **SR-10** Guardado atómico: escribir en un temporal, ejecutar `fsync` y renombrar. Mantener una copia de la versión anterior hasta confirmar el guardado.

### Sincronización local (nuevo frente a KeePass)
- **SR-11** **Emparejamiento explícito**: los dispositivos se emparejan una vez escaneando un código QR que contiene la clave pública efímera X25519 y una huella. Después se confirma un código corto (SAS) en ambas pantallas. Sin emparejamiento no hay sincronización.
- **SR-12** Canal cifrado y autenticado con claves estáticas fijadas: **Noise XX** o TLS 1.3 con certificados autofirmados *pinned* tras el emparejamiento. Debe ofrecer secreto hacia adelante.
- **SR-13** **Solo viaja el `.kdbx` cifrado**: el canal añade una segunda capa, pero el receptor necesita igualmente la clave maestra. Nunca se transmiten la contraseña ni la clave derivada.
- **SR-14** Descubrimiento con mDNS/DNS-SD sin anunciar datos identificables (nada de nombres de usuario ni de bases de datos en el TXT). El servicio solo escucha mientras la sincronización está activa y la app está desbloqueada, o bajo demanda.
- **SR-15** Fusión determinista al estilo KeePass: por UUID y `LastModificationTime`, conservando el historial y respetando `DeletedObjects`. Nunca se pierde una versión: ante un conflicto se conservan ambas en el historial.
- **SR-16** Revocación de dispositivos: lista de dispositivos de confianza dentro de la propia base de datos, en `CustomData`. Revocar uno exige cambiar la clave maestra si se sospecha que está comprometido.
- **SR-17** Protección frente a reversión (*rollback*): no aceptar una copia más antigua que la última conocida de ese dispositivo sin confirmación explícita.

### Proceso
- **SR-18** Sin telemetría ni conexiones salientes por defecto.
- **SR-19** Sin plugins con plena confianza en la v1. Si se añaden, deben ir en sandbox y con permisos declarados.
- **SR-20** Revisión de dependencias (SBOM y escaneo de vulnerabilidades en CI), *fuzzing* del lector de KDBX y auditoría externa antes de la 1.0.
