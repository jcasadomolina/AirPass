# Apuntes de AirPass

Apuntes técnicos y teóricos de lo que voy aprendiendo mientras construyo AirPass.

- **[diario.md](diario.md)**: qué se hizo en cada sesión, en orden cronológico, con enlaces a los temas.
- **[temas/](temas/)**: un fichero por concepto. Cada uno tiene la idea clave, cómo funciona, cómo lo aplica KeePass, qué decide AirPass y unas preguntas de repaso.

## Índice de temas

| # | Tema | Conceptos |
|---|---|---|
| 01 | [Integridad de lo que descargas](temas/01-integridad-de-descargas.md) | Funciones hash, SHA-256, verificación de software |
| 02 | [Ingeniería inversa estática](temas/02-ingenieria-inversa-estatica.md) | .NET/CIL, P/Invoke, observación frente a inferencia |
| 03 | [Derivación de claves (KDF)](temas/03-derivacion-de-claves.md) | Contraseña frente a clave, sal, AES-KDF, Argon2 |
| 04 | [Cifrado autenticado](temas/04-cifrado-autenticado.md) | Confidencialidad frente a integridad, HMAC, Encrypt-then-MAC, separación de claves |
| 05 | [Secretos en memoria](temas/05-secretos-en-memoria.md) | Volcados de memoria, GC, borrado de búferes, CVE-2023-32784 |
| 06 | [Decisiones de arquitectura (ADR)](temas/06-decisiones-de-arquitectura.md) | Estructura de un ADR, compromisos de seguridad, criterios que distinguen |
| 07 | [No inventes tu criptografía](temas/07-no-inventes-tu-criptografia.md) | Primitivos frente a composición, errores típicos, oráculos de prueba, cadena de suministro |
| 08 | [Módulos de Gradle](temas/08-modulos-de-gradle.md) | Subproyectos, plugins frente a dependencias, `implementation` frente a `api`, `src/main` y `src/test` |
| 09 | [Tests y vectores de prueba](temas/09-tests-y-vectores-de-prueba.md) | Anatomía de un test, vectores oficiales, fichero real como oráculo, casos malos |
| 10 | [Leer un formato binario](temas/10-leer-un-formato-binario.md) | Firma, *little-endian*, enteros con signo, TLV, validación de longitudes, cabecera de KDBX 4 |
| 11 | [De la contraseña a las claves](temas/11-de-la-contrasena-a-las-claves.md) | Clave compuesta, AES-KDF, claves de cifrado y de HMAC, hash frente a HMAC, tiempo constante |
| 12 | [Git en el día a día](temas/12-git-en-el-dia-a-dia.md) | *Staging*, dividir commits, gitflow, finales de línea, `.gitignore`, corregir errores |
| 13 | [Autenticar y descifrar el contenido](temas/13-autenticar-y-descifrar-el-contenido.md) | Bloques con HMAC, bloque final, AES-CBC, relleno, *padding oracle*, compresión, cabecera interna |
| 14 | [El XML y los campos protegidos](temas/14-el-xml-y-los-campos-protegidos.md) | Cifrado de flujo, ChaCha20, cifrado interno, orden del flujo, XXE, defensa en profundidad |
| 15 | [La interfaz y los secretos](temas/15-la-interfaz-y-los-secretos.md) | Puerta de entrada única, hilos, mensajes de error, límite de `String`, portapapeles de Windows |
| 16 | [Integración continua y cadena de suministro](temas/16-ci-y-cadena-de-suministro.md) | CI, acciones fijadas por hash, mínimo privilegio, wrapper de Gradle, `SECURITY.md` |
| 17 | [Argon2 y la primera dependencia](temas/17-argon2-y-la-primera-dependencia.md) | Funciones duras en memoria, variantes de Argon2, parámetros no autenticados, BouncyCastle, corpus |
| 18 | [Escribir un fichero sin perder nada](temas/18-escribir-sin-perder-nada.md) | Conservar lo desconocido, CSPRNG, no reutilizar claves ni *nonces*, ida y vuelta, oráculo externo |
