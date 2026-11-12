# 12 · Git en el día a día

## Idea clave
Un commit es una unidad de cambio con sentido propio. El *staging* es lo que permite decidir qué entra en cada uno.

## Las tres zonas
| Zona | Qué contiene | Cómo se mueve algo a la siguiente |
|---|---|---|
| Carpeta de trabajo | Los ficheros tal como están en disco | `git add <ficheros>` |
| *Staging* | Lo que entrará en el próximo commit | `git commit` |
| Historial | Los commits | `git push` lo sube a GitHub |

`git status` muestra en verde lo que está en el *staging* y en rojo lo que queda fuera. Los ficheros nuevos salen como *untracked* hasta el primer `git add`.

## Dividir un trabajo en commits
Se hace con varios `git add` parciales, uno por tema, cada uno seguido de su commit:

```
git add crypto
git commit -m "Título" -m "Descripción"
```

El primer `-m` es el título y el segundo, la descripción. `git commit -am` añade todos los ficheros ya conocidos que hayan cambiado, pero no los nuevos.

## Flujo de una funcionalidad (gitflow)
1. `git checkout develop` y `git pull`.
2. `git checkout -b feature/nombre`.
3. Commits.
4. `git push -u origin feature/nombre`.
5. Pull request contra `develop`, comprobando que la base no sea `main`.

Los cambios sin commitear viajan al cambiar de rama, siempre que no choquen con la rama de destino.

## Renombrar con `git mv`
Git no guarda renombrados: los deduce al comparar el contenido de los commits. `git mv` es un atajo que renombra y actualiza el *staging* a la vez; renombrar a mano y hacer `git add` de lo viejo y lo nuevo da el mismo resultado.

## Finales de línea
Windows usa `CRLF` y Linux, `LF`. Con `* text=auto` en `.gitattributes`, git guarda siempre `LF` en el repositorio y pone `CRLF` en la carpeta de trabajo en Windows. El aviso «LF will be replaced by CRLF» es informativo. La excepción es `gradlew`, un script de Unix que no funciona con `CRLF`.

## Lo que no se sube
`.gitignore` lista lo que git debe ignorar: resultados de compilación (`build/`), configuración local del editor (`.vscode`, `.idea`, `bin/`) y `local.properties`, que contiene rutas de mi máquina.

## Corregir errores
- Fichero de más en el *staging*: `git restore --staged <fichero>`.
- Mensaje del último commit, aún sin subir: `git commit --amend`.
- Commit ya subido o fusionado: **no se reescribe**. Se añade un commit nuevo que corrige. Reescribir el historial de una rama compartida rompe las copias de los demás.

## Antes de cada commit
1. Ejecutar los tests.
2. `git status`, para ver que no se cuela nada inesperado.
3. `git log --oneline` antes del push, para revisar los títulos.

## Preguntas de repaso
1. ¿Qué diferencia hay entre `git add` y `git commit`?
2. ¿Por qué no se corrige con `--amend` un commit que ya está en `develop`?
3. ¿Qué pasa con mis cambios sin commitear si cambio de rama?
