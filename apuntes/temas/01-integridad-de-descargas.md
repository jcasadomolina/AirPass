# 01 · Integridad de lo que descargas

## Idea clave
Antes de confiar en un fichero descargado, hay que comprobar que es **exactamente** el que publicó el autor. Una **función hash criptográfica** lo permite.

## Función hash criptográfica
Convierte una entrada de cualquier tamaño en una huella de tamaño fijo (SHA-256 da 32 bytes). Tiene tres propiedades:
- **Resistencia a preimagen**: dada una huella, no se puede encontrar una entrada que la produzca.
- **Resistencia a segunda preimagen**: dado un fichero, no se puede fabricar otro distinto con la misma huella.
- **Resistencia a colisiones**: no se pueden encontrar dos entradas cualesquiera con la misma huella.

Cambiar un solo bit de la entrada cambia aproximadamente la mitad de los bits de la salida (**efecto avalancha**).

## En la práctica
```bash
sha256sum KeePass-2.62.zip
# 49b008e4ca9e9db9cb22e1173ec0f7676235b5309d7b47213cf81010cd1bbeed
```
Esa huella coincide con la que publica <https://keepass.info/integrity.html>.

## Límite importante
Un hash **no demuestra quién** creó el fichero. Si un atacante controla la web, puede cambiar a la vez el fichero y su hash. Para eso existen las **firmas digitales** (OpenPGP o Authenticode): solo quien tiene la clave privada del autor puede firmar. KeePass también publica firmas OpenPGP, pero no se verificaron en esta sesión.

## En AirPass
- Las releases publicarán su SHA-256 y, más adelante, una firma.
- En KDBX 4, el hash SHA-256 de la cabecera detecta corrupción accidental. Para detectar **manipulación** hace falta un HMAC (ver [tema 04](04-cifrado-autenticado.md)).

## Preguntas de repaso
1. ¿Por qué un hash por sí solo no protege si el atacante controla la web de descarga?
2. ¿Qué diferencia hay entre resistencia a segunda preimagen y resistencia a colisiones?
3. ¿Por qué MD5 y SHA-1 ya no se recomiendan?
