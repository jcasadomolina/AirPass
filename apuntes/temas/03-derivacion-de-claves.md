# 03 · Derivación de claves (KDF)

## Idea clave
Una **contraseña** no es una **clave**. Las contraseñas humanas tienen poca entropía y longitud variable, mientras que un cifrado como AES-256 necesita exactamente 32 bytes aleatorios. Un **KDF** (*Key Derivation Function*) convierte la contraseña en clave y, a propósito, lo hace **lentamente**.

## ¿Por qué lento?
Si alguien roba el `.kdbx`, puede probar contraseñas sin límite en su propio equipo (**ataque offline**). Si cada intento cuesta 1 segundo en lugar de 1 microsegundo, el atacante necesita un millón de veces más tiempo. Para el usuario legítimo, 1 segundo al abrir la base de datos es aceptable.

## Sal (*salt*)
Un valor aleatorio que se guarda en claro junto al fichero y entra en el KDF. Tiene dos efectos:
- Dos usuarios con la misma contraseña obtienen claves distintas.
- Impide usar tablas precalculadas (*rainbow tables*): el atacante tiene que empezar de cero con cada fichero.

## Los KDF de KeePass

| KDF | Cómo frena | Debilidad |
|---|---|---|
| **AES-KDF** | Cifra la clave consigo misma 600 000 veces | Apenas usa memoria, así que las GPU y los ASIC la atacan en paralelo con facilidad |
| **Argon2d** | Necesita mucha memoria (64 MiB por defecto) con accesos que dependen de los datos | Esos accesos pueden filtrar información por canales laterales de caché |
| **Argon2id** | Híbrido: la primera pasada usa accesos independientes de los datos y el resto, dependientes | Recomendado hoy (RFC 9106) |

**Dureza en memoria** (*memory-hard*): una GPU tiene miles de núcleos pero poca memoria por núcleo. Si cada intento necesita 64 MiB, el atacante no puede ejecutar miles de intentos en paralelo.

## La cadena completa en KDBX 4
```
password ──SHA-256──┐
keyfile  ──────────┼─► SHA-256(concatenación) = compositeKey
                    │
compositeKey ──Argon2(sal, t, m, p)──► transformedKey
SHA-256(masterSeed ‖ transformedKey)          = clave de cifrado
SHA-512(masterSeed ‖ transformedKey ‖ 0x01)   = clave HMAC
```
`masterSeed` cambia en cada guardado, así que la clave de cifrado también cambia aunque la contraseña sea la misma.

## En AirPass
- `SR-02`: Argon2id por defecto, calibrado para tardar unos 0,5–1 s en el dispositivo más lento del usuario, con al menos 64 MiB.
- Hay que **leer** Argon2d y AES-KDF para abrir ficheros creados con KeePass.

## Preguntas de repaso
1. ¿Por qué no basta con `SHA-256(password)` como clave?
2. ¿Qué gana el atacante si no hay sal?
3. ¿Por qué un KDF que exige mucha memoria frena más a una GPU que uno que solo exige mucho tiempo?
