# 11 · De la contraseña a las claves

Este tema recorre la cadena tal como está implementada. La teoría de cada pieza está en los temas [03](03-derivacion-de-claves.md) (KDF), [04](04-cifrado-autenticado.md) (HMAC) y [05](05-secretos-en-memoria.md) (secretos en memoria).

## Idea clave
La contraseña nunca cifra nada directamente. Pasa por varios pasos, cada uno con un propósito, y el último permite saber si era la correcta **sin descifrar un solo byte**.

## La cadena
```
contraseña
   │  SHA-256 dos veces
   ▼
clave compuesta (32 bytes)
   │  KDF con los parámetros de la cabecera
   ▼
clave transformada (32 bytes)
   │  se concatena con la semilla maestra
   ├──► SHA-256(semilla ‖ transformada)          ──► clave de cifrado (32 bytes)
   └──► SHA-512(semilla ‖ transformada ‖ 0x01)   ──► clave de HMAC (64 bytes)
```

1. **Clave compuesta.** Cada componente (contraseña, fichero clave) se resume por separado con SHA-256 y después se resume el conjunto. Con solo contraseña, queda un SHA-256 del SHA-256.
2. **KDF.** Hace caro cada intento de adivinar la contraseña. AES-KDF cifra la clave compuesta con AES-256 tantas veces como indiquen las rondas (600 000 en el fichero de prueba) y resume el resultado. Solo gasta tiempo de procesador; Argon2 gasta además memoria y resiste mejor los ataques con tarjetas gráficas.
3. **Dos claves.** Una para cifrar y otra para autenticar: cada clave, un solo uso.

## Hash frente a HMAC
Tras la cabecera, el fichero guarda dos valores de 32 bytes:

| Valor | Clave | Detecta |
|---|---|---|
| SHA-256 de la cabecera | Ninguna | Daños accidentales |
| HMAC-SHA-256 de la cabecera | La derivada de la contraseña | Manipulación deliberada |

Un atacante puede cambiar la cabecera y recalcular el SHA-256, porque no hace falta ninguna clave. No puede recalcular el HMAC. El ataque clásico que esto evita: rebajar las rondas de la KDF escritas en la cabecera.

La clave con la que se autentica la cabecera es `SHA-512(0xFFFFFFFFFFFFFFFF ‖ clave de HMAC)`: la del bloque de índice 2⁶⁴ − 1, reservado para ella.

## Contraseña incorrecta o fichero manipulado
Si la contraseña es incorrecta, las claves salen distintas y el HMAC no coincide. Si alguien ha tocado la cabecera, tampoco. **Las dos situaciones no se pueden distinguir**, y el mensaje de error debe decirlo así.

## Comparar en tiempo constante
Una comparación normal se detiene en el primer byte distinto. Medir cuánto tarda puede revelar cuántos bytes eran correctos. `MessageDigest.isEqual` tarda siempre lo mismo.

## Cómo lo hace KeePass
KeePass 2.62 crea los ficheros nuevos con AES-KDF y 600 000 rondas. KeePassXC usa Argon2 por defecto. KeePass no pone límite al número de rondas que acepta de un fichero.

## Qué hace AirPass
- `derivarClaves` comprueba primero el SHA-256 de la cabecera (error: fichero dañado) y después el HMAC (error: `ClaveIncorrectaException`).
- La contraseña se recibe como `CharArray` y se convierte a UTF-8 sin pasar por `String`. Los valores intermedios se rellenan con ceros en cuanto dejan de hacer falta (`SR-06`).
- Los primitivos (`sha256`, `sha512`, `hmacSha256`, `aesKdf`, `iguales`) están en `crypto` y vienen del JDK. BouncyCastle no ha hecho falta todavía.

**Pendiente**
- Argon2d y Argon2id: necesitan BouncyCastle y un fichero de prueba de KeePassXC.
- Fichero clave como segundo componente.
- Decidir si se limita el número de rondas o se permite cancelar: un fichero malicioso puede declarar miles de millones.
- Si salta una excepción a mitad de la derivación, algún valor intermedio queda sin borrar hasta que actúe el recolector.

## Preguntas de repaso
1. ¿Por qué se derivan dos claves distintas en lugar de usar una para todo?
2. ¿Qué detecta el HMAC de la cabecera que no detecta su SHA-256?
3. ¿Por qué AirPass no puede decir «contraseña incorrecta» con seguridad?
4. ¿Qué información filtra una comparación de bytes que no sea de tiempo constante?
