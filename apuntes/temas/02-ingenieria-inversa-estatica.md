# 02 · Ingeniería inversa estática

## Idea clave
El análisis **estático** estudia un programa **sin ejecutarlo**: su estructura, sus metadatos y su código. Es seguro (no se ejecuta nada desconocido), pero solo muestra lo que el programa *puede* hacer, no lo que *hace* en un momento concreto. Eso último es el análisis **dinámico**.

## Qué se obtuvo de KeePass con REA
- `KeePass.exe` es un ensamblado **.NET** (código CIL, no nativo). Los metadatos .NET contienen los nombres de todos los tipos y métodos, así que se pueden listar sin decompilar: 942 tipos en 36 espacios de nombres.
- **P/Invoke** es el mecanismo de .NET para llamar a funciones nativas de Windows. Su lista es muy reveladora: si un programa declara `SetWindowDisplayAffinity`, probablemente se protege de capturas de pantalla. Si declara `CreateDesktop`, usa un escritorio aislado para pedir la contraseña.
- La DLL nativa `KeePassLibN.x64.dll` exporta funciones `argon2_*`. Argon2 se ejecuta en código nativo porque es mucho más rápido que en C#.

## Disciplina de evidencias
REA separa tres niveles, y conviene hacerlo siempre en seguridad:
- **Observación**: está en el binario o en el código, por ejemplo «declara `WerAddExcludedApplication`».
- **Inferencia**: lo deduzco, por ejemplo «para que los volcados de fallos no contengan secretos».
- **Desconocido**: no lo he comprobado, por ejemplo «qué KDF elige la interfaz al crear una base de datos».

Al redactar los documentos cometí un error: escribí que el KDF por defecto era Argon2d sin haberlo comprobado. Al mirar el código vi que la librería registra AES-KDF en primera posición. Esa es la lección: **una inferencia no es una observación**.

## ¿Es legal?
KeePass es software libre (GPLv2) y su código es público, así que estudiarlo es legítimo. Lo que **no** se puede hacer es copiar su código en AirPass sin cumplir la GPL. Por eso AirPass se implementa a partir de la especificación del formato.

## Preguntas de repaso
1. ¿Qué ventaja y qué limitación tiene el análisis estático frente al dinámico?
2. ¿Por qué es más fácil analizar un programa .NET que uno en C++?
3. Busca tres APIs de la lista de P/Invoke y explica qué defensa implementan.
