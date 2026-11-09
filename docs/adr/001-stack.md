# ADR-001: Stack tecnológico de AirPass

- **Estado:** Propuesto
- **Fecha:** 2026-11-09

## Contexto

AirPass resuelve la **sincronización de contraseñas entre varios dispositivos** de un mismo usuario, con prioridad en la **seguridad** y la **privacidad**: no hay servidor, no hay cuenta y no se envía ningún dato fuera de la red local. La primera versión se dirige a **Windows** (escritorio) y **Android** (móvil).

Restricciones derivadas de [seguridad.md](../seguridad.md) y del [plan de desarrollo](../plan-desarrollo.md):

1. **Un único núcleo** para el formato KDBX, la criptografía y la fusión. La lógica de seguridad debe existir y auditarse **una sola vez**: dos implementaciones divergentes del cifrado o de la fusión son una fuente segura de errores y de pérdida de datos.
2. **Criptografía madura**: AES-256, ChaCha20, HMAC-SHA-256, SHA-2 y **Argon2d/id**. Argon2d es necesario para abrir ficheros creados con KeePass.
3. **Acceso directo a la seguridad de cada plataforma**:
   - Android: Keystore respaldado por hardware, BiometricPrompt, servicio de Autofill y `FLAG_SECURE`.
   - Windows: DPAPI y Windows Hello para el desbloqueo rápido, y exclusión de la ventana en capturas de pantalla.
4. **Red local** para la sincronización: servidor y cliente con TLS 1.3, y descubrimiento por mDNS.
5. Es un proyecto individual, así que el esfuerzo de mantener varias bases de código cuenta.

## Opciones consideradas

### A. Kotlin Multiplatform + Compose Multiplatform
Núcleo en Kotlin común, con la UI en Compose para Android y para escritorio (JVM en Windows).
- ✅ Acceso directo a las APIs de seguridad de Android, sin capas intermedias.
- ✅ BouncyCastle cubre todos los primitivos, incluido Argon2d/id, en Android y en JVM.
- ✅ Ktor sirve para servidor y cliente TLS en ambas plataformas.
- ✅ Experiencia previa en Kotlin/Android.
- ❌ JVM y ART tienen recolector de basura: los secretos pueden quedar copiados en memoria.
- ❌ En Windows, las APIs nativas (DPAPI, Windows Hello) se alcanzan mediante JNA o JNI, no directamente.
- ❌ La app de escritorio incluye su propio JRE, lo que da un instalador más pesado.

### B. .NET MAUI (C#)
Una sola base de código en C# para Windows y Android.
- ✅ Windows es su plataforma principal: DPAPI y Windows Hello directamente.
- ✅ Conozco C#. BouncyCastle también existe en .NET.
- ❌ Android se alcanza mediante enlaces (*bindings*): el servicio de Autofill y el Keystore están peor documentados y tienen menos ejemplos.
- ❌ Tiene el mismo problema de recolector de basura que la opción A.
- ❌ Riesgo de copiar código de KeePass (C#, GPLv2) sin darse cuenta.

### C. Dos aplicaciones nativas (Kotlin en Android y C#/WinUI en Windows)
- ✅ La mejor integración posible con cada plataforma.
- ❌ **Dos implementaciones del núcleo criptográfico y de la fusión**, lo que incumple la restricción 1. Es inaceptable para un gestor de contraseñas.

### D. Núcleo en Rust + UIs nativas
- ✅ El mejor control de memoria (`zeroize`, sin recolector de basura) y RustCrypto.
- ❌ Dos lenguajes, FFI en ambas plataformas y un lenguaje nuevo: el esfuerzo se dispara para un proyecto individual.

## Decisión

Se adopta **Kotlin Multiplatform con Compose Multiplatform**, con los destinos **Android** y **escritorio JVM (Windows)**.

## Justificación

- Es la única opción que cumple a la vez la restricción 1 (un núcleo) y da acceso **directo** a la plataforma donde la seguridad es más compleja (Android: Autofill, Keystore y biometría).
- En Windows, lo que falta (DPAPI, Windows Hello y la exclusión de capturas) son pocas llamadas concretas. Se resuelven con JNA en una capa `actual` pequeña y fácil de auditar.
- El problema del recolector de basura es común a A y B, de modo que no inclina la decisión. Rust lo resuelve, pero a un coste desproporcionado.

## Consecuencias

**Positivas**
- El núcleo (`core`, `crypto` y `sync`) se escribe y prueba una vez y se usa en las dos plataformas.
- La interfaz también se comparte en su mayor parte.

**Negativas y riesgos asumidos**
- **Secretos en memoria**: con recolector de basura no se puede garantizar el borrado. Mitigación (`SR-06`):
  - Prohibido guardar secretos en `String`; se usan `ByteArray`/`CharArray` que se rellenan con ceros tras usarse.
  - Los secretos están descifrados el mínimo tiempo posible.
  - Se verifica con volcados de memoria en la fase F2.
- **Capa nativa en Windows**: requiere JNA y código específico de la plataforma (`expect`/`actual`) para DPAPI, Windows Hello y `SetWindowDisplayAffinity`. Esa capa se revisa con especial cuidado.
- **Instalador de escritorio** más pesado por el JRE incluido (jpackage/MSI). Se acepta.
- **Dependencia de BouncyCastle** como proveedor criptográfico. Su versión se fija y se vigila en la CI (`SR-20`).

**Futuro**
- macOS y Linux funcionan con el mismo destino JVM, con un coste bajo.
- iOS es posible con Kotlin/Native, pero BouncyCastle no está disponible ahí: haría falta otro proveedor criptográfico. Se decidirá en un ADR propio si llega el momento.
