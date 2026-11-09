# Análisis de KeePass 2.62

Referencia funcional y técnica para diseñar AirPass. Fuente: KeePass 2.62 portable y su código fuente oficial (descargados de keepass.info / SourceForge el 2026-11-09, SHA-256 cotejado con <https://keepass.info/integrity.html>).

| Artefacto | SHA-256 |
|---|---|
| `KeePass-2.62.zip` | `49b008e4ca9e9db9cb22e1173ec0f7676235b5309d7b47213cf81010cd1bbeed` (coincide con el oficial) |
| `KeePass-2.62-Source.zip` | `9c07b0539a013ee5e22b26fd806e472c1858bc2f3f7b3b4406f4404774263ac0` (coincide con el oficial) |
| `KeePass.exe` | `afb89b74ae1b8519f483d600095f4df8d86628178af6acdc5f2b23888c89c3f3` |

## Método

1. **Inspección estática del binario con REA 6.8.0** (sin ejecutar KeePass):
   - `inspect-managed-artifact` → identidad .NET, referencias, recursos (`ev_0c88e001…`).
   - `inspect-managed-members` → inventario de 942 tipos en 36 espacios de nombres (`ev_48164342…`).
   - `inspect-managed-native-boundaries` → todas las llamadas P/Invoke a APIs nativas (`ev_8a0003b0…`).
   - `inspect-native-exports` y `inspect-pe-load-config` sobre `KeePassLibN.x64.dll` (`ev_f1679117…`, `ev_1c4ae7c5…`).
2. **Contraste con el código fuente** oficial para las constantes criptográficas exactas.

Cada afirmación se marca como **[Obs]** (observado en el binario o en el código), **[Inf]** (deducido) o **[?]** (desconocido o no verificado).

## 1. Arquitectura

- **[Obs]** Ensamblado .NET puro (`il-only`, firmado con strong name), versión 2.62.0.0, metadatos CLR v2.0.50727. Depende solo de la BCL: `mscorlib`, `System`, `System.Xml`, `System.Drawing`, `System.Windows.Forms`, `System.Core` y `System.Security`. No usa librerías de terceros.
- **[Obs]** `KeePassLib` (núcleo: modelo, criptografía y serialización) va compilado dentro de `KeePass.exe`. La interfaz es WinForms (`KeePass.Forms`, `KeePass.UI`).
- **[Obs]** Hay tres DLL nativas auxiliares, `KeePassLibN.{x32,x64,a64}.dll`, que exportan Argon2 (familia `argon2*`), `AesKdfTransformHalf` y `AuxProtectProcessWithDacl`. Así derivan claves en código nativo, más rápido que en C#. En Linux usa `libargon2.so` y `libgcrypt.so.20`.
- **[Obs]** `KeePassLibN.x64.dll` declara CFG (`IMAGE_GUARD_CF_INSTRUMENTED`).
- **[Obs]** Tiene un sistema de plugins (`KeePass.Plugins`: `PluginManager`, `IPluginHost`, `PlgxCache`). Los `.plgx` se compilan en la máquina del usuario.
- **[Obs]** Tiene un motor de disparadores ECAS (`KeePass.Ecas`) con eventos, condiciones y acciones que puede configurar el usuario.

### Mapa de módulos del núcleo [Obs, inventario REA]

| Espacio de nombres | Tipos clave | Responsabilidad |
|---|---|---|
| `KeePassLib.Cryptography.Cipher` | `StandardAesEngine`, `ChaCha20Engine`, `ChaCha20Cipher`, `Salsa20Cipher`, `CtrBlockCipher` | Cifrado externo (AES-256-CBC o ChaCha20) y cifrado interno de campos |
| `KeePassLib.Cryptography.KeyDerivation` | `Argon2Kdf`, `AesKdf`, `KdfParameters`, `KdfPool` | Derivación de la clave maestra |
| `KeePassLib.Cryptography.Hash` | `Blake2b` | Usado por Argon2 |
| `KeePassLib.Cryptography` | `CryptoRandom`, `CryptoRandomStream`, `HmacOtp`, `QualityEstimation`, `PopularPasswords`, `SelfTest` | Aleatoriedad, OTP, calidad de contraseñas y autotest criptográfico al arrancar |
| `KeePassLib.Cryptography.PasswordGenerator` | `CharSetBasedGenerator`, `PatternBasedGenerator`, `PwProfile` | Generador de contraseñas |
| `KeePassLib.Keys` | `CompositeKey`, `KcpPassword`, `KcpKeyFile`, `KcpUserAccount`, `KfxFile`, `KeyProviderPool` | Clave compuesta (contraseña, fichero clave y cuenta de Windows) |
| `KeePassLib.Security` | `ProtectedString`, `ProtectedBinary`, `XorredBuffer` | Secretos cifrados en memoria |
| `KeePassLib.Serialization` | `KdbxFile`, `HmacBlockStream`, `HashedBlockStream`, `FileTransactionEx`, `FileLock`, `IOConnection` | Formato KDBX, escritura atómica y E/S local o remota |

## 2. Funcionalidades

**Base de datos y modelo**
- Una base de datos `.kdbx` contiene un árbol de **grupos** con **entradas**. Cada entrada tiene campos estándar (Title, UserName, Password, URL, Notes), campos personalizados, adjuntos binarios, etiquetas, icono, colores, caducidad e **historial de versiones**. Todo se identifica por UUID.
- Plantillas de entradas, papelera de reciclaje y campos de datos personalizados (`CustomData`).
- Referencias entre campos (`{REF:P@I:…}`) y marcadores de sustitución (`{USERNAME}`, `{PASSWORD}`…).

**Desbloqueo (clave compuesta)**
- Contraseña maestra, **fichero clave** (formato XML `KfxFile` v2.0 con hash de verificación, o binario) y/o **cuenta de usuario de Windows** (DPAPI). Se pueden combinar.
- Proveedores de clave mediante plugins (`KeyProviderPool`), por ejemplo para YubiKey.

**Uso diario**
- Copiar al portapapeles con borrado automático, que por defecto ocurre a los **12 s** [Obs `AceSecurity.cs`].
- **Auto-Type**: escribe usuario y contraseña en la ventana activa simulando el teclado (`SendInput`), según secuencias por entrada o por ventana. Se activa con una tecla rápida global (`RegisterHotKey`).
- Búsqueda, filtros, informes (contraseñas débiles, duplicadas o caducadas), generador de contraseñas con perfiles y estimación de calidad.
- Abrir URL e integración con el navegador mediante plugins (KeePassRPC, KeePassHttp).

**Gestión de ficheros**
- Guardado **transaccional**: escribe en un fichero temporal y lo reemplaza de forma atómica. Usa `MoveFileTransacted` (TxF) cuando el sistema lo admite [Obs P/Invoke `KtmW32!CreateTransaction/CommitTransaction`].
- Bloqueo del fichero entre instancias (`FileLock`).
- **Sincronización** de dos bases de datos con una fusión basada en UUID y fechas de modificación, que conserva el historial y respeta los objetos borrados (`DeletedObjects`).
- Almacenamiento remoto por URL (FTP, HTTP o WebDAV) mediante `IOConnection`.
- Importación y exportación de numerosos formatos (CSV, XML y otros gestores). Impresión y «hoja de emergencia».

## 3. Formato de fichero KDBX 4.1 [Obs código fuente]

```
┌──────────────────────── Cabecera externa (texto claro, autenticada) ────────────────────────┐
│ Firma 0x9AA2D903 0xB54BFB67 │ Versión 0x00040001                                              │
│ TLV: CipherID · CompressionFlags · MasterSeed(32) · EncryptionIV · KdfParameters · PublicCustomData │
├──────────────────────────────────────────────────────────────────────────────────────────────┤
│ SHA-256(cabecera)  │  HMAC-SHA-256(cabecera)                                                  │
├──────────────────────── Carga útil: HmacBlockStream ─────────────────────────────────────────┤
│ Bloques [HMAC(32) · tamaño(4) · datos] ── descifrado AES-256-CBC / ChaCha20 ── GZip opcional │
│   ├─ Cabecera interna: InnerRandomStreamID (ChaCha20) · clave · adjuntos binarios            │
│   └─ XML: Meta + Root (grupos, entradas, historial, DeletedObjects)                          │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Derivación de claves**
1. Cada componente de la clave aporta 32 bytes: la contraseña como `SHA-256(UTF-8)`, el fichero clave como hash o datos de 32 bytes y la cuenta de usuario como clave protegida con DPAPI.
2. `compositeKey = SHA-256(componente₁ ‖ componente₂ ‖ …)`.
3. `transformed = KDF(compositeKey, KdfParameters)`. Hay tres KDF: **AES-KDF** (600 000 rondas por defecto), **Argon2d** y **Argon2id** (por defecto 2 iteraciones, **64 MiB**, paralelismo 2, sal de 32 bytes y versión 0x13). `KdfPool` registra AES-KDF en primera posición, así que es el valor por defecto de `PwDatabase` en la librería [Obs]. No se ha comprobado qué KDF elige la interfaz al crear una base de datos nueva [?].
4. `cipherKey = SHA-256(MasterSeed ‖ transformed)`.
5. `hmacKey = SHA-512(MasterSeed ‖ transformed ‖ 0x01)`. La clave de cada bloque es `SHA-512(índice₆₄ ‖ hmacKey)`. La cabecera usa el índice `UInt64.MaxValue`.

**Protección interna**: los campos marcados como protegidos (por defecto solo `Password`) se cifran dentro del XML con ChaCha20, usando una clave aleatoria que va en la cabecera interna.

## 4. Requisitos no funcionales observados

- **Portabilidad**: funciona sin instalación (versión portable) en Windows y, con Mono, en Linux y macOS.
- **Sin servidor y sin telemetría**: no hay sincronización en la nube propia. La única conexión de red por defecto es la comprobación de actualizaciones (`UpdateCheckEx`), que se puede desactivar.
- **Compatibilidad hacia atrás**: lee KDBX 3.1 y, mediante importación, KDB 1.x.
- **Autoverificación**: `SelfTest` comprueba los algoritmos criptográficos al iniciar.
- **Extensibilidad**: plugins, disparadores, tipos de URL personalizados y traducciones.

## 5. Lo que no se ha verificado [?]

- No se ejecutó KeePass, así que nada de este documento describe comportamiento observado en ejecución.
- `inspect-pe-certificates` falló al analizar la firma Authenticode de `KeePass.exe` (ASN.1 con más de 32 niveles de anidamiento, límite de REA). La firma no se ha verificado; solo se ha cotejado el hash del ZIP.
- El análisis de seguridad en profundidad está en [seguridad.md](seguridad.md).
