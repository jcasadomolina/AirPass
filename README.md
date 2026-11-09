# AirPass
Herramienta de gestor de contraseñas multiplataforma, sincronización local entre dispositivos móviles y ordenador.

- **Windows y Android** en la primera versión.
- **Sin nube**: tus contraseñas solo están en tus dispositivos.
- **Compatible con KeePass**: usa el formato abierto KDBX 4.1, así que puedes abrir tu base de datos con KeePass, KeePassXC o KeePassDX.
- **Sincronización por la red local** entre dispositivos emparejados con un código QR.

> Estado: **planificación**. Todavía no hay código. Empieza por la fase F0 del plan.

## Documentación

| Documento | Contenido |
|---|---|
| [docs/analisis-keepass.md](docs/analisis-keepass.md) | Cómo funciona KeePass: arquitectura, funciones y formato KDBX (analizado con REA y el código fuente) |
| [docs/seguridad.md](docs/seguridad.md) | Modelo de amenazas, defensas de KeePass y requisitos de seguridad de AirPass (`SR-01`…`SR-20`) |
| [docs/plan-desarrollo.md](docs/plan-desarrollo.md) | Stack, arquitectura, requisitos funcionales, fases F0–F5, pruebas y riesgos |

## Cómo empezar

### 1. Requisitos
- **JDK 21** (Temurin recomendado)
- **Android Studio** (última versión estable) con el plugin de Kotlin Multiplatform
- **Git**
- Opcional: **KeePass 2.62+** o **KeePassXC** para generar ficheros `.kdbx` de prueba

### 2. Clonar
```bash
git clone https://github.com/jcasadomolina/AirPass.git
cd AirPass
```

### 3. Primeras tareas (fase F0)
1. **Stack**: Kotlin Multiplatform con Compose Multiplatform, documentado en [docs/adr/001-stack.md](docs/adr/001-stack.md).
2. **Generar el esqueleto** con el asistente de Kotlin Multiplatform (Android + Desktop) y reorganizarlo en los módulos `core`, `crypto`, `sync`, `shared`, `app-desktop` y `app-android`.
3. **Evaluar la librería KDBX** (kotpass u otra) contra ficheros creados con KeePass y anotar la decisión en `docs/adr/002-kdbx.md`.
4. **Configurar la CI** con GitHub Actions: compilar y probar en Windows y Android.
5. Añadir `SECURITY.md` con el canal privado para comunicar vulnerabilidades.

Cuando exista el código, se compilará con:
```bash
./gradlew build
```

### 4. Reglas de contribución
- Nada de criptografía propia: solo primitivos de BouncyCastle o libsodium.
- Ningún secreto en `String`; usa búferes que se borren tras usarse (`SR-06`).
- Los cambios en `core`, `crypto` o `sync` necesitan pruebas y una revisión centrada en seguridad.
- No copies código de KeePass (GPLv2): implementa a partir de la especificación del formato.
