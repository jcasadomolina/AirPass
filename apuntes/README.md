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
