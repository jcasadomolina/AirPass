# 07 · «No inventes tu propia criptografía» y qué sí puedes construir

## Idea clave
La regla «*don't roll your own crypto*» no significa «no toques nada de criptografía». Hay que distinguir tres niveles:

| Nivel | Ejemplos | ¿Lo hago yo? |
|---|---|---|
| **Primitivos** | AES, ChaCha20, SHA-256, Argon2, HMAC | **Nunca**. Uso una librería auditada (BouncyCastle) |
| **Construcciones y protocolos nuevos** | Inventar un modo de cifrado o un protocolo de intercambio de claves | **Nunca**. Uso estándares (TLS 1.3, Noise) |
| **Implementar un formato o protocolo ya especificado** | Leer y escribir KDBX, usar TLS 1.3 con una librería | **Sí**, con pruebas contra una referencia |

AirPass hace lo tercero: compone primitivos estándar siguiendo la especificación pública de KDBX.

## Errores típicos al componer primitivos (aunque sean buenos)
- **Reutilizar un IV o nonce**: con ChaCha20 o AES-CTR, cifrar dos mensajes con la misma clave y el mismo nonce permite recuperar el XOR de los textos claros. KDBX genera un IV y un `MasterSeed` nuevos en cada guardado (`SR-04`).
- **Descifrar antes de verificar el MAC**: abre la puerta a los *padding oracles* ([tema 04](04-cifrado-autenticado.md)).
- **Comparar etiquetas con `==`**: filtra información por el tiempo que tarda. Hay que usar una comparación en tiempo constante (`MessageDigest.isEqual`).
- **Usar la misma clave para dos fines**: por eso KDBX deriva claves distintas para el cifrado y para el HMAC.
- **Usar un generador aleatorio inseguro**: `java.util.Random` es predecible. Hay que usar `SecureRandom`.

## Oráculo de pruebas
Un **oráculo** es una fuente externa de verdad con la que comparar resultados. En AirPass hay dos:
- **Vectores de prueba de los RFC**: entradas y salidas conocidas para cada primitivo (RFC 8439 para ChaCha20, RFC 9106 para Argon2, RFC 4231 para HMAC).
- **Ficheros `.kdbx` reales** creados con KeePass y KeePassXC. Si AirPass los abre y genera ficheros que KeePass abre, la composición es correcta.

Los ficheros **manipulados a propósito** son igual de importantes: una implementación que acepta un fichero con el HMAC roto es peor que una que falla.

## Riesgo de cadena de suministro
Cada dependencia es código de terceros que se ejecuta con acceso a los secretos. Una librería pequeña y poco mantenida para la parte más crítica (como kotpass para KDBX) concentra el riesgo. BouncyCastle sí se acepta porque es grande, auditada y muy usada. Aun así, su versión se fija y se vigila (`SR-20`).

## Preguntas de repaso
1. ¿En qué nivel de la tabla está «implementar KDBX»? ¿Y «inventar mi propio formato cifrado»?
2. ¿Qué ocurre si se reutiliza el nonce de ChaCha20 con la misma clave?
3. ¿Por qué un fichero manipulado que se rechaza demuestra más que uno válido que se abre?
