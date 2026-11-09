# 04 · Cifrado autenticado

## Idea clave
Cifrar da **confidencialidad**: nadie puede leer los datos. Pero no da **integridad**: alguien podría *modificarlos* sin saber qué dicen. Un gestor de contraseñas necesita las dos cosas.

## ¿Se puede modificar algo cifrado sin la clave?
Sí. En AES-CBC, cambiar un bit de un bloque cifrado cambia el **mismo bit** del siguiente bloque descifrado. En un cifrado de flujo como ChaCha20, cambiar un bit del cifrado cambia exactamente ese bit del texto claro. Este ataque de **maleabilidad** permite alterar datos de forma predecible. Otro ejemplo, en KDBX 3.1, cuya cabecera no estaba autenticada: un atacante podía rebajar las rondas del KDF escritas en la cabecera.

## MAC y HMAC
Un **MAC** (*Message Authentication Code*) es una etiqueta que solo puede calcular quien conoce la clave. Si un byte cambia, la etiqueta deja de coincidir.
**HMAC** construye un MAC a partir de una función hash: `HMAC(K, m) = H((K ⊕ opad) ‖ H((K ⊕ ipad) ‖ m))`.
La etiqueta se compara en **tiempo constante**, para que el tiempo que tarda la comparación no revele cuántos bytes coinciden.

## Encrypt-then-MAC
Primero se cifra y después se calcula el MAC **del texto cifrado**. Al leer, se verifica el MAC **antes** de descifrar nada. Así nunca se procesan datos manipulados. Esto evita los ataques de *padding oracle* que sufren otros órdenes, como MAC-then-encrypt.

## Cómo lo hace KDBX 4
- **Cabecera**: va en claro, pero la protege un HMAC-SHA-256.
- **Carga útil**: se divide en bloques. Cada bloque lleva `HMAC(clave_bloque, índice ‖ tamaño ‖ datos)`, y la clave de cada bloque se calcula como `SHA-512(índice ‖ hmacKey)`. Como el MAC incluye el **índice**, no se pueden reordenar, borrar ni duplicar bloques. Un bloque final vacío detecta que alguien haya **truncado** el fichero.
- **Separación de claves**: la clave de cifrado y la de HMAC son **distintas**, aunque se derivan del mismo secreto. Usar una misma clave para dos fines puede crear interacciones peligrosas entre algoritmos.

## Alternativa moderna: AEAD
**AES-GCM** y **ChaCha20-Poly1305** cifran y autentican en un solo paso (*Authenticated Encryption with Associated Data*). KDBX es anterior a su adopción generalizada y por eso compone las piezas a mano. Para el canal de sincronización de AirPass usaremos AEAD, ya que TLS 1.3 solo admite estos algoritmos.

## Preguntas de repaso
1. Pon un ejemplo de cómo modificar un fichero cifrado sin conocer la clave.
2. ¿Por qué hay que verificar el MAC *antes* de descifrar?
3. ¿Qué problema evita incluir el índice del bloque en el HMAC?
