# 14 · El XML y los campos protegidos

## Idea clave
Dentro del fichero ya descifrado, las contraseñas siguen cifradas con una segunda capa. No protege el fichero: protege la **memoria** del programa que lo tiene abierto.

## Estructura del XML
```xml
<KeePassFile>
  <Root>
    <Group>
      <Name>Nombre del grupo</Name>
      <Entry>
        <String><Key>Title</Key><Value>Entrada de prueba</Value></String>
        <String><Key>Password</Key><Value Protected="True">kX9v…</Value></String>
        <History> …versiones anteriores… </History>
      </Entry>
      <Group> …subgrupos… </Group>
    </Group>
  </Root>
</KeePassFile>
```

Una entrada es una lista de pares clave-valor. Los nombres estándar son `Title`, `UserName`, `Password`, `URL` y `Notes`; el usuario puede añadir otros.

## Cifrado de bloque y cifrado de flujo
| | De bloque (AES) | De flujo (ChaCha20) |
|---|---|---|
| Cómo trabaja | Transforma bloques de 16 bytes | Genera una secuencia de bytes y la combina con los datos mediante XOR |
| Relleno | Necesario | No hace falta |
| Cifrar y descifrar | Operaciones distintas | La misma operación |
| Parámetro público | Vector de inicialización | *Nonce* |

El XOR se deshace aplicándolo otra vez: `(dato ⊕ flujo) ⊕ flujo = dato`.

**La regla del cifrado de flujo:** no reutilizar nunca la misma clave con el mismo *nonce*. Si dos mensajes se cifran con el mismo flujo, al hacer XOR de los dos cifrados el flujo desaparece y queda el XOR de los dos textos claros.

## El cifrado interno de KDBX
- La clave interna de 64 bytes viene en la cabecera interna y se genera de nuevo cada vez que se guarda el fichero.
- Se le aplica SHA-512: los primeros 32 bytes son la clave de ChaCha20 y los 12 siguientes, el *nonce*.
- Los valores con `Protected="True"` son el resultado de ese cifrado, en Base64.

**Hay un único flujo para todo el documento.** Cada valor protegido consume el trozo que sigue al anterior, en el orden en que aparecen en el XML. Hay que descifrarlos todos y en orden, incluidos los del historial. Si se salta uno, el flujo queda desplazado y los siguientes salen como basura **sin ningún error**.

## Para qué sirve la segunda capa
El programa puede tener el documento cargado para mostrar títulos y usuarios sin tener todas las contraseñas en claro. Cada una se descifra cuando hace falta. Ante un volcado de memoria, hay menos que encontrar. Es una mitigación, no una barrera: la clave interna también está en memoria. Ver el [tema 05](05-secretos-en-memoria.md).

## XXE: el XML como vector de ataque
XML permite declarar, en un DTD, entidades que se sustituyen por el contenido de un fichero o de una dirección web:

```xml
<!DOCTYPE x [<!ENTITY secreto SYSTEM "file:///C:/Windows/win.ini">]>
<Name>&secreto;</Name>
```

Un analizador con la configuración por defecto lee ese fichero y lo inserta en el documento. Es el ataque **XXE** (*XML External Entity*). Variantes: leer ficheros locales, hacer peticiones a la red interna o agotar la memoria con entidades que se expanden unas dentro de otras.

La defensa es prohibir los DTD:

```kotlin
fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
```

En KDBX el XML va cifrado y autenticado, así que solo podría prepararlo quien conoce la clave. Se desactiva igualmente por **defensa en profundidad**: no cuesta nada y protege si el XML llega algún día por otro camino, como una importación.

## Cómo lo hace KeePass
KeePass descifra los valores protegidos al cargar y los vuelve a proteger en memoria con su clase `ProtectedString`, que en Windows se apoya en DPAPI. Por defecto solo protege el campo `Password`; el resto se puede activar en los ajustes de la base de datos.

## Qué hace AirPass
- `leerXmlKdbx` analiza el XML con los DTD desactivados y convierte los errores del analizador en `IllegalArgumentException`.
- Recorre todos los elementos con `Protected="True"` en el orden del documento y los descifra con un único `FlujoChaCha20`.
- Modelo mínimo: `Grupo` (nombre, subgrupos y entradas) y `Entrada` (`campos` como `String` y `protegidos` como `ByteArray`).
- El historial se descifra para no desplazar el flujo, pero no se conserva: sus bytes se borran.

**Pendiente**
- Las contraseñas quedan en claro en memoria tras abrir el fichero. Falta decidir cómo protegerlas (`SR-06`).
- Historial, identificadores, fechas, etiquetas, iconos y adjuntos.
- La papelera se lee como un grupo más.

## Preguntas de repaso
1. ¿Por qué en ChaCha20 cifrar y descifrar son la misma operación?
2. ¿Qué ocurre si se reutilizan la clave y el *nonce* con dos mensajes distintos?
3. ¿Por qué hay que descifrar las contraseñas del historial aunque no se muestren?
4. ¿De qué protege el cifrado interno y de qué no?
5. ¿Qué es XXE y por qué se desactivan los DTD aunque el XML esté autenticado?
