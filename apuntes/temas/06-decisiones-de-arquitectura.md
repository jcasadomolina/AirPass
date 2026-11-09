# 06 · Decisiones de arquitectura (ADR)

## Idea clave
Un **ADR** (*Architecture Decision Record*) es un documento corto que registra **una** decisión importante: el contexto, las opciones, la elegida, el porqué y sus consecuencias. Sirve para que en el futuro alguien, incluido yo mismo, entienda *por qué* el sistema es así, y no solo *cómo* es.

## Estructura
| Sección | Pregunta que responde |
|---|---|
| Estado | ¿Propuesto, Aceptado, Sustituido por ADR-00X? |
| Contexto | ¿Qué problema y qué restricciones hay? |
| Opciones | ¿Qué alternativas reales había, con sus pros y contras? |
| Decisión | ¿Qué se eligió, en una frase? |
| Justificación | ¿Por qué esta opción y no las demás? |
| Consecuencias | ¿Qué se gana, qué se pierde y qué riesgos se asumen? |

Un ADR aceptado **no se edita** para cambiar la decisión. Se escribe uno nuevo que lo sustituye, y así queda el historial de cómo evolucionó el razonamiento.

## Por qué importa en seguridad
- Las decisiones de seguridad suelen ser **compromisos**: más control frente a más esfuerzo, o comodidad frente a protección. El ADR deja el compromiso a la vista.
- La sección de **consecuencias** obliga a nombrar los riesgos asumidos y su mitigación. Por ejemplo, en el ADR-001: el recolector de basura copia secretos en memoria, y se mitiga con `SR-06`.
- Un buen criterio de decisión tiene que **distinguir** entre las opciones. El recolector de basura afecta igual a Kotlin y a .NET, así que no ayuda a elegir entre ellos, aunque sí descarta frente a Rust.

## Los ADR de AirPass
| ADR | Decisión | Riesgo principal asumido |
|---|---|---|
| [001](../../docs/adr/001-stack.md) | Kotlin Multiplatform, Windows + Android | Secretos en memoria con recolector de basura; capa nativa de Windows con JNA |
| [002](../../docs/adr/002-kdbx.md) | KDBX implementado por mí y validado con ficheros reales | Más tiempo de desarrollo; errores de interpretación de la especificación |

## Preguntas de repaso
1. ¿Por qué no se edita un ADR aceptado cuando cambia la decisión?
2. En el ADR-001, ¿qué criterio descartó la opción de dos apps nativas y por qué es especialmente grave en un gestor de contraseñas?
3. Escribe una consecuencia negativa del ADR-002 que no esté en el documento.
