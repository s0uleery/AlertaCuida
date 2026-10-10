## Requisitos del entorno de desarrollo

Sistema operativo: Windows 11 (64 bits)

| Herramienta | Versión | Distribución / fuente | Verificar con |
|---|---|---|---|
| JDK | 21.0.12.1 (LTS) | Eclipse Temurin (Adoptium) | `java -version` y `javac -version` |
| Maven | 3.10.0 | Apache Maven | `mvn -v` |
| Git | 2.55.0 | Git for Windows | `git --version` |
| Node.js | 26.x (completar versión exacta) | nodejs.org | `node -v` |
| npm | (completar) | Incluido con Node.js | `npm -v` |
| IDE | IntelliJ IDEA (completar versión) | JetBrains | Help → About |


### Codificación

El proyecto usa UTF-8. `mvn -v` debe mostrar `platform encoding: UTF-8`.