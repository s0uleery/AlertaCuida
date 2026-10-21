## Requisitos del entorno de desarrollo

Sistema operativo: Windows 11 (64 bits)

| Herramienta | Versión | Distribución / fuente | Verificar con |
|---|---|---|---|
| JDK | 21.0.12.1 (LTS) | Eclipse Temurin (Adoptium) | `java -version` y `javac -version` |
| Maven | 3.10.0 | Apache Maven | `mvn -v` |
| Git | 2.55.0 | Git for Windows | `git --version` |
| Node.js | 26.11.1 | nodejs.org | `node -v` |
| npm | 11.12.1 | Incluido con Node.js | `npm -v` |
| IDE | IntelliJ IDEA 2026.2 | JetBrains | Help → About |
| Docker | 29.0.1 | Docker Desktop | `docker --version` |
| Docker Compose | 2.x | Incluido en Docker Desktop | `docker compose version` |
| PostgreSQL | 18 (`postgres:18-alpine`) | Imagen de Docker, no se instala | `docker compose ps` |


### Codificación

El proyecto usa UTF-8. `mvn -v` debe mostrar `platform encoding: UTF-8`.