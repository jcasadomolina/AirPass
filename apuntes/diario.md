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
