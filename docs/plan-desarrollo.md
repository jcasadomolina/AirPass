# Plan de desarrollo de AirPass

**AirPass** es un gestor de contraseñas multiplataforma (escritorio y móvil) que funciona sin conexión y sincroniza los dispositivos por la red local, sin nube. Toma como referencia KeePass ([analisis-keepass.md](analisis-keepass.md)) y cumple los requisitos de seguridad de [seguridad.md](seguridad.md) (`SR-xx`).

## 1. Principios

1. **Compatible con KDBX 4.1**: el usuario puede abrir su `.kdbx` en KeePass, KeePassXC o KeePassDX y volver. Así no queda atrapado en AirPass y se reutiliza un formato ya auditado.
2. **Local primero**: no hay servidor ni cuenta. La sincronización ocurre solo entre dispositivos emparejados.
3. **No se escribe criptografía propia**: primitivos de librerías auditadas y, si es viable, una librería KDBX existente.
4. **Un único núcleo compartido** por todas las plataformas, con la interfaz nativa de cada una.

## 2. Stack recomendado

| Capa | Elección | Motivo |
|---|---|---|
| Lenguaje | **Kotlin Multiplatform** (KMP) | Un único núcleo para Android y escritorio (JVM), con iOS posible más adelante |
| UI | **Compose Multiplatform** | Una sola base de UI para Android, Windows, macOS y Linux |
| KDBX | Evaluar **kotpass** (Kotlin, KDBX 4) y, si no encaja, implementación propia sobre la especificación | Evita reescribir el parser y el writer (`SR-01`) |
| Criptografía | **BouncyCastle** (AES-256-CBC, ChaCha20, Argon2d/id, HMAC, SHA-2) | Admite Argon2d, necesario para leer ficheros de KeePass. libsodium solo trae Argon2id |
| Red local | **Ktor** (servidor y cliente) con TLS 1.3 y *pinning*. Descubrimiento con mDNS (`NsdManager` en Android y JmDNS en escritorio) | `SR-11`…`SR-14` |
| Almacén seguro | Android Keystore y BiometricPrompt. Escritorio: DPAPI, Keychain o Secret Service | `SR-07` |
| Build y CI | Gradle (Kotlin DSL) y GitHub Actions | Pruebas en Linux, Windows y macOS más Android |

> **Decisión abierta**: si se prefiere la mejor experiencia nativa en iOS desde el principio, las alternativas son Flutter, o un núcleo en Rust con UIs nativas. Se fija en el ADR-001 antes de la fase 1.

## 3. Arquitectura

```
┌───────────────┐  ┌───────────────┐
│ app-desktop   │  │ app-android   │   UI Compose · Autofill (Android) · biometría
└──────┬────────┘  └──────┬────────┘
       └─────────┬────────┘
          ┌──────▼──────┐
          │   shared    │  ViewModels · casos de uso · bloqueo · portapapeles (expect/actual)
          └──────┬──────┘
   ┌─────────────┼──────────────┐
┌──▼───┐   ┌─────▼─────┐   ┌────▼─────┐
│ core │   │  crypto   │   │  sync    │  emparejamiento · transporte TLS · fusión
│ KDBX │   │ (BC/KMP)  │   │          │
└──────┘   └───────────┘   └──────────┘
```

- `core`: modelo (grupos, entradas, historial), lectura y escritura de KDBX y fusión (`SR-15`). No sabe nada de UI ni de red.
- `crypto`: fachada mínima sobre BouncyCastle, con autotest (`SR-03`) y búferes que se borran tras usarse (`SR-06`).
- `sync`: no descifra nunca la base de datos. Transporta el blob cifrado y delega la fusión en `core` (`SR-13`).

## 4. Requisitos funcionales

Prioridad: **M** (imprescindible en la v1), **S** (debería), **C** (podría), **W** (no en la v1).

| ID | Requisito | Prioridad |
|---|---|---|
| RF-01 | Crear, abrir y guardar bases de datos KDBX 4.1 con contraseña y/o fichero clave | M |
| RF-02 | Grupos, entradas, campos personalizados, adjuntos, etiquetas e historial | M |
| RF-03 | Búsqueda y filtros | M |
| RF-04 | Generador de contraseñas con perfiles y estimación de fortaleza | M |
| RF-05 | Copiar al portapapeles con borrado automático | M |
| RF-06 | Bloqueo automático y desbloqueo biométrico | M |
| RF-07 | Autofill en Android | M |
| RF-08 | Emparejar dispositivos por QR y sincronizar por LAN | M |
| RF-09 | Fusión sin pérdida (historial y borrados) | M |
| RF-10 | Papelera de reciclaje | S |
| RF-11 | Códigos TOTP (formato compatible con KeePassXC) | S |
| RF-12 | Informes: contraseñas débiles, reutilizadas o caducadas | S |
| RF-13 | Importación desde CSV, Bitwarden y 1Password | S |
| RF-14 | Auto-Type en escritorio | C |
| RF-15 | Extensión de navegador | C |
| RF-16 | Plugins, disparadores y almacenamiento remoto FTP/WebDAV | W |

## 5. Fases

| Fase | Objetivo | Entregables | Criterio de salida |
|---|---|---|---|
| **F0 · Fundaciones** (1–2 sem.) | Repositorio listo | Esqueleto KMP con los módulos de §3, CI en 3 SO más Android, ADR-001 (stack) y ADR-002 (librería KDBX), `SECURITY.md` | CI en verde y el *hello world* arranca en escritorio y Android |
| **F1 · Núcleo KDBX** (3–4 sem.) | Leer y escribir KDBX 4.1 | `core` y `crypto`, autotest y fixtures creadas con KeePass 2.62 (AES-KDF, Argon2d, Argon2id, ChaCha20 y fichero clave) | Ida y vuelta KeePass → AirPass → KeePass sin pérdidas. Tests de HMAC inválido (`SR-05`). Fuzzing básico del lector |
| **F2 · Escritorio MVP** (3 sem.) | Uso diario en PC | RF-01…06 en `app-desktop`, guardado atómico (`SR-10`) y portapapeles seguro | Pruebas de uso. Volcado de memoria sin la contraseña maestra (`SR-06`) |
| **F3 · Android MVP** (4 sem.) | Uso diario en móvil | RF-01…07, Keystore y biometría (`SR-07`), `FLAG_SECURE` y `allowBackup=false` (`SR-08`) | Autofill funcionando en Chrome y en las apps principales |
| **F4 · Sincronización local** (4–5 sem.) | Mantener los dispositivos al día | Emparejamiento QR+SAS, TLS 1.3 con *pinning*, mDNS, fusión y anti-*rollback* (`SR-11`…`SR-17`) | Escenarios de conflicto automatizados (editar la misma entrada en dos dispositivos, borrar frente a editar). Revocación |
| **F5 · Endurecimiento y 1.0** (3+ sem.) | Publicar | RF-10…13, SBOM, escaneo de dependencias, fuzzing continuo y auditoría externa (`SR-20`) | Auditoría sin hallazgos críticos abiertos |
| **Posterior** | Ampliar | iOS, extensión de navegador, passkeys y Auto-Type | — |

## 6. Estrategia de pruebas

- **Compatibilidad**: un corpus de ficheros `.kdbx` generados con KeePass, KeePassXC y KeePassDX en `core/src/test/resources`. Cada versión debe abrirlos todos y producir ficheros que esas apps abran.
- **Criptografía**: vectores oficiales de AES, ChaCha20 (RFC 8439), Argon2 (RFC 9106) y HMAC (RFC 4231).
- **Fusión**: pruebas de propiedades (*property-based*). Fusionar A con B da lo mismo que fusionar B con A, y fusionar A consigo misma no cambia nada.
- **Seguridad**: fuzzing del parser, análisis de volcados de memoria en escritorio y Android y una lista de comprobación de OWASP MASVS en móvil.

## 7. Riesgos

| Riesgo | Mitigación |
|---|---|
| Argon2 en JVM puro es lento en móviles de gama baja | Calibrar los parámetros por dispositivo y valorar una versión nativa (JNI) en F3 |
| Los GC de JVM y ART copian secretos en memoria | `ByteArray`/`CharArray` que se borran tras usarse, mínimo tiempo descifrado y verificación con volcados |
| Fusión incorrecta que pierda datos | Copia previa a cada sincronización, pruebas de propiedades y fusión que nunca descarta versiones |
| Restricciones de red en Android (segundo plano, Wi-Fi con aislamiento de clientes) | Sincronización iniciada por el usuario con la app en primer plano y error claro si mDNS no encuentra pares |
| Licencias | KeePass es GPLv2: **no copiar su código**. Implementar a partir de la especificación del formato y comprobar las licencias de las dependencias |
