# AlertaCuida

Plataforma de teleasistencia del PRLAC: recibe las alertas de los relojes de los pacientes, avisa a sus contactos y las muestra en un dashboard. Plan de trabajo en [`docs/cronograma.md`](docs/cronograma.md).

## Compilar y probar

Herramientas y versiones: [`Versiones.md`](Versiones.md).

```
./mvnw verify
```

Compila los módulos, revisa el estilo con Checkstyle y corre las pruebas. GitHub Actions hace lo mismo en cada push.

Para ejecutar el backend:

```
java -jar app/target/alertacuida-app-0.0.1-SNAPSHOT.jar
```

## Probar en local

Necesita Docker Desktop abierto. La primera vez, copiar `.env.example` como `.env` (credenciales de la base local, no se sube al repositorio): `Copy-Item .env.example .env` en PowerShell o `cp .env.example .env` en Mac y Linux.

1. **Base de datos** (PostgreSQL 18 en Docker). Esperar a que `docker compose ps` diga `healthy`:
   ```
   docker compose up -d
   ```
2. **Backend** en `http://localhost:8080`, con el perfil `dev` (claves de desarrollo y datos de ejemplo):
   ```
   ./mvnw install -DskipTests
   ./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev
   ```
3. **Dashboard** en `http://localhost:5173` (reenvía `/api` al backend):
   ```
   cd frontend
   npm install
   npm run dev
   ```

Usuario de prueba (solo perfil `dev`, en `db/ejemplo/R__datos_de_ejemplo.sql`): RUN `11.111.111-1`, contraseña `Admin1234!`, rol ADMINISTRADOR.

## Estructura

| Módulo | Contiene | Depende de |
|---|---|---|
| `domain` | Entidades y reglas del negocio, en Java puro (sin Spring) | — |
| `application` | Casos de uso y puertos (interfaces) | `domain` |
| `adapters` | API REST, base de datos, correo, FCM | `application` |
| `app` | Arranque de Spring Boot y configuración | `adapters` |
| `frontend` | Dashboard en React, TypeScript, Vite y Tailwind | API del backend |

## Convenciones

- **Nombres:** conceptos del negocio en español y términos técnicos en inglés (`Paciente`, `AlertaRepository`, `registrarAlerta()`), sin tildes ni `ñ`. Tabs en Java y XML (`.editorconfig`); reglas de estilo en `config/checkstyle/checkstyle.xml`.
- **Ramas:** desde `develop`, con el número de tarea: `feature/0.4-modelo-datos`, `fix/1.4-event-id-duplicado`.
- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/es/v1.0.0/) con tipo en inglés y descripción en español: `feat(0.4): agregar entidad Paciente`. Tipos: `feat`, `fix`, `test`, `docs`, `refactor`, `ci`, `chore`.
- **Pull requests:** hacia `develop`, con el CI en verde y la revisión de otra persona del equipo.