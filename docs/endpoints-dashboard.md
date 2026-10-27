# Endpoints del dashboard (frontend)

Lo que el backend **ya atiende** para el dashboard, al 26 de octubre de 2026. Es una referencia rápida para quien trabaja en el frontend; el porqué de cada regla está en [backend.md](backend.md). La API del reloj va aparte, en [endpoints-reloj.md](endpoints-reloj.md).

La misma información, en formato OpenAPI, está en [api-dashboard.yaml](api-dashboard.yaml). Para verla con formato y probar los endpoints, abrirla en [editor.swagger.io](https://editor.swagger.io) o en la extensión OpenAPI de VS Code.

En desarrollo, el front corre en `http://localhost:5173` y Vite reenvía `/api` al backend (`http://localhost:8080`). Usuario de ejemplo (perfil `dev`): RUN `11.111.111-1`, contraseña `Admin1234!`.

Los tipos de TypeScript de cada respuesta ya están en `frontend/src/features/*/tipos.ts`, y las llamadas, en `*Api.ts`.

## Reglas comunes

- **Token:** todo exige `Authorization: Bearer <token>`, salvo `POST /api/auth/login` y `POST /api/auth/activar`. El token sale del login.
- **Permisos:** van en el token (`usuario.permisos`, por ejemplo `"USUARIOS:VER"`). Sin token, **401**; con token pero sin el permiso, **403**. Si cambian los permisos de alguien, se notan al iniciar sesión de nuevo.
- **"Usuarios" = pacientes:** el módulo `USUARIOS` son las personas mayores (en el código, "pacientes"). Las cuentas del equipo están en `ADMINISTRACION`, que solo tiene el administrador.
- **Errores, en dos formatos:**
  - Las reglas del negocio (400, 404, 409 y el 401 del login) responden RFC 9457 (`application/problem+json`). El texto para mostrar viene en `detail`:
    ```json
    { "title": "Conflict", "status": 409, "detail": "El usuario ya tiene un reloj: usa reemplazar", "instance": "/api/pacientes/…/reloj" }
    ```
  - Los errores de forma (falta un campo obligatorio, JSON mal escrito, UUID o valor de enumeración inválido) responden con el formato por defecto de Spring, **sin `detail`**: `{ "timestamp": "…", "status": 400, "error": "Bad Request", "path": "…" }`. El front debe tener un mensaje genérico para ese caso.
  - Los 401 (sin token o vencido) y 403 (sin permiso) vienen sin cuerpo.
- **Formatos:** ids UUID; fechas `AAAA-MM-DD`; instantes ISO 8601 en UTC (`2026-10-26T14:00:00Z`); horas `HH:mm`.
- **Respuestas sin cuerpo:** los `204` no traen nada; los `201` de alta traen `{ "id": "…" }` salvo que se indique otra cosa.

## Resumen

| Método y ruta | Permiso | Respuesta |
|---|---|---|
| **Sesión** | | |
| `POST /api/auth/login` | público | 200 `RespuestaLogin` |
| `GET /api/auth/yo` | token | 200 `Usuario` |
| `POST /api/auth/activar` | público | 204 |
| **Cuentas del equipo** | | |
| `POST /api/personal` | `ADMINISTRACION:CREAR` | 201 `RespuestaEnlace` |
| `POST /api/personal/{id}/enlace-activacion` | `ADMINISTRACION:CREAR` | 200 `RespuestaEnlace` |
| **Pacientes** | | |
| `GET /api/pacientes?incluirBajas=false` | `USUARIOS:VER` | 200 `ResumenPaciente[]` |
| `GET /api/pacientes/{id}` | `USUARIOS:VER` | 200 `DetallePaciente` |
| `POST /api/pacientes` | `USUARIOS:CREAR` | 201 `{id}` |
| `PUT /api/pacientes/{id}` | `USUARIOS:EDITAR` | 204 |
| `POST /api/pacientes/{id}/baja` | `USUARIOS:ELIMINAR` | 204 |
| **Enfermedades y situaciones** | | |
| `GET /api/pacientes/{id}/condiciones` | `USUARIOS:VER` | 200 `Condicion[]` |
| `POST /api/pacientes/{id}/condiciones` | `USUARIOS:EDITAR` | 201 `{id}` |
| `DELETE /api/pacientes/{id}/condiciones/{idCondicion}` | `USUARIOS:EDITAR` | 204 |
| **Perfil clínico** | | |
| `GET /api/pacientes/{id}/perfil-clinico` | `USUARIOS:VER` | 200 `PerfilClinico` |
| `PUT /api/pacientes/{id}/perfil-clinico/ajustes` | `USUARIOS:EDITAR` | 204 |
| `PUT /api/pacientes/{id}/perfil-clinico/umbrales/{FC\|SPO2}` | `USUARIOS:EDITAR` | 204 |
| `DELETE /api/pacientes/{id}/perfil-clinico/umbrales/{FC\|SPO2}` | `USUARIOS:EDITAR` | 204 |
| **Contactos de emergencia** | | |
| `GET /api/tipos-contacto` | `USUARIOS:VER` | 200 `TipoContacto[]` |
| `GET /api/pacientes/{id}/contactos` | `USUARIOS:VER` | 200 `ListaContactos` |
| `POST /api/pacientes/{id}/contactos` | `USUARIOS:EDITAR` | 201 `{id}` |
| `PUT /api/pacientes/{id}/contactos/{idContacto}` | `USUARIOS:EDITAR` | 204 |
| `PUT /api/pacientes/{id}/contactos/{idContacto}/opt-in` | `USUARIOS:EDITAR` | 204 |
| `POST /api/pacientes/{id}/contactos/{idContacto}/desactivar` | `USUARIOS:EDITAR` | 204 |
| `POST /api/pacientes/{id}/contactos/{idContacto}/reactivar` | `USUARIOS:EDITAR` | 204 |
| `PUT /api/pacientes/{id}/contactos/orden` | `USUARIOS:EDITAR` | 204 |
| **Reloj de un paciente** | | |
| `POST /api/pacientes/{id}/reloj` | `USUARIOS:EDITAR` | 201 `TokenReloj` |
| `POST /api/pacientes/{id}/reloj/reemplazo` | `USUARIOS:EDITAR` | 200 `TokenReloj` |
| `DELETE /api/pacientes/{id}/reloj` | `USUARIOS:EDITAR` | 204 |
| `POST /api/pacientes/{id}/reloj/token` | `USUARIOS:EDITAR` | 200 `TokenReloj` |
| **Inventario de relojes** | | |
| `GET /api/relojes` | `USUARIOS:VER` | 200 `RelojConPaciente[]` |
| `POST /api/relojes` | `USUARIOS:CREAR` | 201 `{id}` |
| `PUT /api/relojes/{id}` | `USUARIOS:EDITAR` | 204 |
| **Calendario** | | |
| `GET /api/calendario` | solo sesión | 200 `Calendario` |

## Sesión

### `POST /api/auth/login`
```json
{ "run": "11.111.111-1", "contrasena": "Admin1234!" }
```
Respuesta 200:
```json
{
  "token": "eyJhbGciOi…",
  "expiraEn": "2026-10-26T22:00:00Z",
  "usuario": { "id": "…", "nombre": "Administrador de prueba", "rol": "ADMINISTRADOR", "permisos": ["ADMINISTRACION:CREAR", "USUARIOS:VER", "…"] }
}
```
401 con el mismo mensaje para RUN inexistente o contraseña mala. La cuenta se bloquea con 5 fallos en 15 minutos.

### `GET /api/auth/yo`
Devuelve el `usuario` del token (mismo formato que arriba). Sirve para recuperar la sesión al recargar la página.

### `POST /api/auth/activar`
La persona llega desde el enlace de activación (`/activar?token=…`) y elige su contraseña (mínimo 12 caracteres).
```json
{ "token": "el-del-enlace", "contrasena": "una-contraseña-larga" }
```
400 si el enlace no sirve o venció.

## Cuentas del equipo

### `POST /api/personal`
```json
{ "run": "12.345.678-5", "nombre": "Camila Vera", "cargo": "Enfermera", "correo": "camila@ejemplo.cl", "rol": "PERSONAL" }
```
`rol`: `ADMINISTRADOR`, `COORDINADOR` o `PERSONAL`; 400 si no existe o está desactivado. Respuesta 201:
```json
{ "idUsuario": "…", "enlaceActivacion": "http://localhost:5173/activar?token=…", "expiraEn": "2026-10-28T14:00:00Z" }
```
409 si el RUN o el correo ya tienen cuenta.

### `POST /api/personal/{id}/enlace-activacion`
Otro enlace para una cuenta que aún no se activa; el anterior deja de servir. Misma respuesta. 404 si la cuenta no existe o ya está activa.

## Pacientes

### `GET /api/pacientes?incluirBajas=false`
```json
[
  {
    "paciente": {
      "id": "…",
      "datos": {
        "run": "9.876.543-2", "nombres": "María", "apellidos": "Soto Rojas", "alias": "Mary",
        "fechaNacimiento": "1944-03-12", "sexo": "FEMENINO", "direccion": "Calle 123",
        "nivelDependencia": "MODERADA", "fechaConsentimiento": "2026-10-01", "fechaIngreso": "2026-10-01"
      },
      "fechaBaja": null
    },
    "edad": 82,
    "relojActual": "SM-R910-0001"
  }
]
```

### `GET /api/pacientes/{id}`
Como un elemento de la lista, pero con el historial de relojes en vez de `relojActual`:
```json
{
  "paciente": { "id": "…", "datos": { "…": "…" }, "fechaBaja": null },
  "edad": 82,
  "historial": [
    { "idAsignacion": "…", "idDispositivo": "…", "identificadorReloj": "SM-R910-0001", "inicio": "2026-10-02T15:00:00Z", "fin": null }
  ]
}
```

### `POST /api/pacientes` y `PUT /api/pacientes/{id}`
El cuerpo es `datos` (los mismos campos de arriba). Obligatorios: `run`, `nombres`, `apellidos`, `fechaIngreso`. `sexo`: `FEMENINO` o `MASCULINO`; `nivelDependencia`: `MODERADA` o `SEVERA`. 409 si el RUN ya está registrado, o si se quita la fecha de consentimiento a alguien que tiene reloj.

### `POST /api/pacientes/{id}/baja`
Sin cuerpo. Deja la baja con la fecha de hoy y cierra el reloj que tenía. No borra nada.

## Enfermedades y situaciones

`GET` devuelve `[{ "id": "…", "tipo": "ENFERMEDAD", "descripcion": "Diabetes tipo 2" }]`: primero las enfermedades y luego las situaciones, en orden alfabético.

`POST`:
```json
{ "tipo": "SITUACION", "descripcion": "Vive sola" }
```
`tipo`: `ENFERMEDAD` o `SITUACION`. 409 si ya la tiene.

## Perfil clínico

### `GET /api/pacientes/{id}/perfil-clinico`
```json
{
  "ajustes": {
    "modulos": { "caidas": true, "fc": true, "spo2": true, "inactividad": false, "tendencia": false },
    "gpsActivo": false, "modoConservador": false, "minutosEspera": null, "motivo": null
  },
  "ajustesDesde": null,
  "minutosEspera": 5,
  "fc":   { "umbral": { "graveBajo": 40, "normalMin": 50, "normalMax": 110, "graveAlto": 130 }, "propio": false, "desde": null },
  "spo2": { "umbral": { "graveBajo": 88, "normalMin": 92, "normalMax": null, "graveAlto": null }, "propio": false, "desde": null }
}
```
- `minutosEspera` de afuera es el **vigente** (el propio o el base); el de `ajustes` es `null` si el paciente usa el base.
- `propio: false` indica que el umbral es el **valor base** (definido en `application.properties`; provisional hasta que lo valide el equipo clínico).

### `PUT …/perfil-clinico/ajustes`
El cuerpo es `ajustes` (como arriba). `motivo` hasta 500 caracteres.

### `PUT …/perfil-clinico/umbrales/{FC|SPO2}`
```json
{ "graveBajo": 45, "normalMin": 55, "normalMax": 100, "graveAlto": 120 }
```
Deben ir en orden creciente. Para SpO2 solo se usan `graveBajo` y `normalMin`.

### `DELETE …/perfil-clinico/umbrales/{FC|SPO2}`
Vuelve al umbral base.

## Contactos de emergencia

### `GET /api/tipos-contacto`
`[{ "codigo": "FAMILIA", "nombre": "Familia" }, { "codigo": "VECINO", "nombre": "Vecino o vecina" }, …]` (también `PRLAC`, `INSTITUCION`, `SEGURIDAD_CIUDADANA`, `OTRO`)

### `GET /api/pacientes/{id}/contactos`
```json
{
  "contactos": [
    {
      "id": "…", "nombre": "Pedro Soto", "whatsapp": "+56912345678", "correo": null,
      "optInVigente": true, "fechaOptIn": "2026-10-20T13:00:00Z", "fechaRevocacion": null,
      "tipo": "FAMILIA", "parentesco": "Hijo", "cuidadorPrincipal": true,
      "prioridad": 1, "horaInicio": "08:00", "horaFin": "22:00",
      "activo": true, "otrosPacientes": 0, "canales": ["WHATSAPP"]
    }
  ],
  "sinCobertura": [ { "inicio": "22:00", "fin": "08:00" } ]
}
```
- Primero van los activos, por `prioridad`, y después los desactivados.
- `sinCobertura`: horas del día en que ningún contacto activo recibe avisos. Es una advertencia, no un error.
- `otrosPacientes`: de cuántos otros pacientes es contacto esta misma persona.

### `POST /api/pacientes/{id}/contactos` y `PUT …/contactos/{idContacto}`
```json
{
  "nombre": "Pedro Soto", "whatsapp": "+56912345678", "correo": null,
  "tipo": "FAMILIA", "parentesco": "Hijo", "cuidadorPrincipal": true,
  "horaInicio": "08:00", "horaFin": "22:00"
}
```
- Hay que poner al menos WhatsApp o correo. Sin horario, recibe avisos las 24 horas.
- `parentesco` solo se indica si el tipo es `FAMILIA`.
- El alta lo deja al final de la lista.
- No se aceptan los números de emergencia (SAMU, 131).
- Si cambia el WhatsApp, se pierde el opt-in.
- Solo puede haber un cuidador principal, y no puede estar desactivado.
- 409 si ese WhatsApp o correo ya es de otra persona registrada (el `detail` dice de quién).

### `PUT …/contactos/{idContacto}/opt-in`
`{ "aceptado": true }` registra que aceptó recibir WhatsApp; `false`, que lo revocó.

### `POST …/contactos/{idContacto}/desactivar` y `/reactivar`
Sin cuerpo. Desactivar lo saca de la lista sin borrarlo; reactivar lo devuelve al final.

### `PUT /api/pacientes/{id}/contactos/orden`
```json
{ "orden": ["idContacto1", "idContacto2", "idContacto3"] }
```
Debe traer **todos** los activos, cada uno una vez.

## Reloj de un paciente

### `POST /api/pacientes/{id}/reloj` y `…/reloj/reemplazo`
```json
{ "idDispositivo": "…", "estadoRelojAnterior": "EN_REPARACION" }
```
`estadoRelojAnterior` solo se usa en el reemplazo (`OPERATIVO`, `EN_REPARACION` o `RETIRADO`). Respuesta:
```json
{ "idAsignacion": "…", "token": "el-token-del-reloj" }
```
**El token se muestra una sola vez:** hay que mostrarlo para cargarlo en el reloj, porque no se puede volver a consultar. Errores:
- Vincular: 400 si el paciente no tiene fecha de consentimiento; 409 si ya tiene reloj, está de baja, o el reloj no está operativo o lo usa otro paciente.
- Reemplazar: 400 si el reloj nuevo es el mismo; 409 si el paciente no tiene reloj o si el nuevo no está disponible.

### `DELETE /api/pacientes/{id}/reloj`
Retira el reloj y su token deja de servir. 409 si el paciente no tenía reloj.

### `POST /api/pacientes/{id}/reloj/token`
Emite otro token para el mismo reloj (por ejemplo, si se perdió). El anterior deja de servir.

## Inventario de relojes

### `GET /api/relojes`
```json
[
  {
    "reloj": {
      "id": "…",
      "datos": {
        "identificadorReloj": "SM-R910-0001", "modelo": "Galaxy Watch 7 LTE",
        "iccid": "8956…", "numeroSim": "+56987654321",
        "fechaVencimientoPlan": "2027-01-31", "estado": "OPERATIVO"
      }
    },
    "idPaciente": "…",
    "nombrePaciente": "María Soto Rojas"
  }
]
```
`idPaciente` y `nombrePaciente` son `null` si el reloj no está asignado.

### `POST /api/relojes` y `PUT /api/relojes/{id}`
El cuerpo es `datos`. Obligatorio: `identificadorReloj`. 409 si el identificador ya existe, o si se marca como no operativo un reloj que alguien está usando.

## Calendario

### `GET /api/calendario`
Lo ve todo el equipo: basta con tener sesión. Es de solo lectura; el horario y los feriados están en `calendario.yml` y cambiarlos requiere reiniciar el backend.
```json
{
  "zonaHoraria": "America/Santiago",
  "horarioHabil": [
    { "dia": "LUNES", "inicio": "08:30", "fin": "17:30" },
    { "dia": "VIERNES", "inicio": "08:30", "fin": "16:30" }
  ],
  "feriados": [
    { "fecha": "2026-01-01", "nombre": "Año Nuevo" },
    { "fecha": "2026-04-03", "nombre": "Viernes Santo" }
  ]
}
```
- `horarioHabil` trae un tramo por día hábil (en el ejemplo se omiten martes a jueves). Los días que no aparecen no son hábiles. El `inicio` cuenta; a la hora de `fin` ya es fuera de horario.
- `feriados` vienen ordenados por fecha: los nacionales del año en curso y el siguiente.

## Todavía no existen

El front no debe llamarlos aún. Si los necesita, puede usar datos de ejemplo con el mismo formato, como hace hoy la página de inicio.

| Qué | Para | Cronograma |
|---|---|---|
| `/api/alertas` (lista, detalle con línea de tiempo, resolver) | Página de inicio y alertas activas. El formato que espera el front está en `features/alertas/tipos.ts` | 3.4 |
| Panel de estado general (última sincronización, estado del reloj) | Página de inicio | 3.4.1 |
| Tendencias de FC y SpO2, ubicaciones | Gráficos en la ficha | 3.5 |
| Reportes diarios y semanales | Reportes | 3.6 |
| Alertas pendientes de día hábil | Bandeja | 3.7 |
| Estado técnico de los relojes (batería, versión, conexión, plan por vencer) | Inventario | 3.8 |
| Cierre de sesión y renovación del token | Sesión | 1.3.4 |
| Listado y edición de cuentas del equipo | Administración | sin tarea aún |
