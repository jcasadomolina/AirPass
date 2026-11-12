# Política de seguridad

AirPass es un gestor de contraseñas en desarrollo. Es un proyecto personal de aprendizaje: **todavía no hay ninguna versión publicada y no debe usarse para guardar contraseñas reales.**

## Versiones con soporte

| Versión | Soporte |
|---|---|
| Rama `develop` | Sí |
| Versiones publicadas | Todavía no hay ninguna |

## Cómo avisar de una vulnerabilidad

**No abras un *issue* público.** Un fallo de seguridad descrito en público puede aprovecharse antes de que esté corregido.

Usa el aviso privado de GitHub: en la pestaña **Security** del repositorio, pulsa **Report a vulnerability**. Solo lo ve el mantenedor.

Incluye, si puedes:

- qué has encontrado y qué permite hacer a un atacante;
- los pasos para reproducirlo, o un fichero `.kdbx` de prueba que lo provoque (nunca uno con datos reales);
- la versión o el commit en el que lo has visto.

## Qué puedes esperar

Es un proyecto que mantiene una sola persona en su tiempo libre. Intentaré responder en una semana, y te mantendré al tanto del arreglo. Si quieres, tu nombre aparecerá en la nota de la corrección.

## Qué entra en el alcance

- Lectura de ficheros KDBX: cualquier fichero que haga fallar el lector, agote la memoria o se acepte habiendo sido manipulado.
- Criptografía: derivación de claves, verificación de integridad y descifrado.
- Tratamiento de los secretos en memoria, en el portapapeles y en disco.
- La cadena de suministro del proyecto: dependencias y flujos de integración continua.

## Qué queda fuera

- Ataques que requieren que el equipo ya esté comprometido con los mismos privilegios que el usuario (por ejemplo, un *keylogger*). Ningún gestor de contraseñas puede defenderse de eso; está descrito en el [modelo de amenazas](docs/seguridad.md).
- Vulnerabilidades de KeePass, KeePassXC o del propio formato KDBX: deben comunicarse a sus proyectos.
- Las limitaciones ya documentadas como pendientes en `docs/` y `apuntes/`.
