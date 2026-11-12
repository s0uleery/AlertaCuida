# Endpoints del dashboard (frontend)

Lo que el backend **ya atiende** para el dashboard, al 26 de octubre de 2026. Es una referencia rápida para quien trabaja en el frontend; el porqué de cada regla está en [backend.md](backend.md). La API del reloj va aparte, en [endpoints-reloj.md](endpoints-reloj.md).

La misma información, en formato OpenAPI, está en [api-dashboard.yaml](api-dashboard.yaml). Para verla con formato y probar los endpoints, abrirla en [editor.swagger.io](https://editor.swagger.io) o en la extensión OpenAPI de VS Code.

En desarrollo, el front corre en `http://localhost:5173` y Vite reenvía `/api` al backend (`http://localhost:8080`). Usuario de ejemplo (perfil `dev`): RUN `11.111.111-1`, contraseña `Admin1234!`.

Los tipos de TypeScript de cada respuesta ya están en `frontend/src/features/*/tipos.ts`, y las llamadas, en `*Api.ts`.

## Reglas comunes

- **Token:** todo exige `Authorization: Bearer <token>`, salvo `POST /api/auth/login`, `renovar`, `salir` y `activar`. El token sale del login o de la renovación. Esas rutas públicas no deben llevar un token vencido, porque Spring Security las rechazaría con 401.
- **Permisos:** van en el token (`usuario.permisos`, por ejemplo `"USUARIOS:VER"`). Sin token, **401**; con token pero sin el permiso, **403**. Si cambian los permisos de alguien, se notan en su siguiente renovación.
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
| `POST /api/auth/renovar` | cookie de renovación | 200 `RespuestaLogin` |
| `POST /api/auth/salir` | público | 204 |
| `GET /api/auth/yo` | token | 200 `Usuario` |
| `POST /api/auth/activar` | público | 204 |
| **Cuentas del equipo** | | |
| `POST /api/personal` | `ADMINISTRACION:CREAR` | 201 `RespuestaEnlace` |
| `POST /api/personal/{id}/enlace-activacion` | `ADMINISTRACION:CREAR` | 200 `RespuestaEnlace` |
| **Auditoría** | | |
| `GET /api/auditoria?limite=500&incluirLecturas=false` | `ADMINISTRACION:VER` | 200 `RegistroAuditoria[]` |
| **Alertas** | | |
| `GET /api/alertas` | `ALERTAS:VER` | 200 `ResumenAlerta[]` |
| `GET /api/alertas/{id}` | `ALERTAS:VER` | 200 `DetalleAlerta` |
| `POST /api/alertas/{id}/asumir` | `ALERTAS:EDITAR` | 204 |
| `POST /api/alertas/{id}/resolver` | `ALERTAS:EDITAR` | 204 |
| `POST /api/alertas/{id}/seguimiento/cerrar` | `ALERTAS:EDITAR` | 204 |
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
| `PUT /api/pacientes/{id}/perfil-clinico/zona-segura` | `USUARIOS:EDITAR` | 204 |
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
| `GET /api/pacientes/{id}/reloj/configuracion` | `USUARIOS:VER` | 200 `ConfiguracionAplicada` |
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
  "inactividadSegundos": 900,
  "usuario": { "id": "…", "nombre": "Administrador de prueba", "rol": "ADMINISTRADOR", "permisos": ["ADMINISTRACION:CREAR", "USUARIOS:VER", "…"] }
}
```
401 con el mismo mensaje para RUN inexistente o contraseña mala. La cuenta se bloquea con 5 fallos en 15 minutos. Además deja el token de renovación en la cookie `alertacuida_renovacion` (`HttpOnly`, `SameSite=Strict`, `Path=/api/auth`).

### `POST /api/auth/renovar`
Sin cuerpo: el token viaja en la cookie, que se reemplaza en cada renovación. Responde 200 con el mismo formato del login. Sirve para recuperar la sesión al recargar la página y para renovar el token antes de que venza. Responde 401 con `detail` y borra la cookie si la sesión terminó: sin cookie, 10 horas desde el login, 15 minutos sin renovar, token reutilizado o cuenta desactivada.

### `POST /api/auth/salir`
Sin cuerpo. Cierra la sesión de este navegador y borra la cookie. Responde 204 aunque no haya sesión.

### `GET /api/auth/yo`
Devuelve el `usuario` del token (mismo formato que arriba).

### `GET /api/auditoria`
Registros del más nuevo al más antiguo. `limite` se ajusta al rango de 1 a 1000 (por defecto, 500). Las acciones `LEER` solo vienen con `incluirLecturas=true`. No trae datos personales del objeto, solo su identificador.
```json
[{ "id": "…", "fechaHora": "2026-11-03T14:00:00Z", "accion": "LEER", "entidad": "PACIENTE", "idEntidad": "…", "detalle": null, "usuario": { "id": "…", "nombre": "Ana" }, "ip": "10.0.0.5" }]
```
`entidad` e `idEntidad` son `null` en los inicios de sesión. `usuario` es `null` en un login fallido con un RUN que no existe.

### `POST /api/auth/activar`
La persona llega desde el enlace de activación (`/activar?token=…`) y elige su contraseña (mínimo 12 caracteres).
```json
{ "token": "el-del-enlace", "contrasena": "una-contraseña-larga" }
```
400 si el enlace no sirve o venció.

## Cuentas del equipo

### `POST /api/personal`
```json
{ "run": "12.345.678-5", "nombre": "Camila Vera", "cargo": "Enfermera", "correo": "camila@ejemplo.cl", "rol": "COORDINADOR",
  "telefono": "+56 9 1234 5678", "aceptaWhatsapp": true }
```
`rol`: `ADMINISTRADOR`, `COORDINADOR` o `PERSONAL`; 400 si no existe o está desactivado. `telefono` (celular con WhatsApp) es obligatorio para `COORDINADOR`, porque recibe los avisos de la oficina PRLAC; se guarda normalizado y cifrado. `aceptaWhatsapp: true` registra el opt-in con la fecha de hoy y exige el teléfono. Respuesta 201:
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
    "modulos": { "caidas": true, "fc": true, "spo2": true, "inactividad": false, "tendencia": false,
                 "zonaSegura": true, "botonAyuda": true },
    "gpsActivo": true, "modoConservador": false, "minutosEspera": null, "motivo": null
  },
  "ajustesDesde": null,
  "minutosEspera": 5,
  "fc":   { "umbral": { "graveBajo": 40, "normalMin": 50, "normalMax": 110, "graveAlto": 130 }, "propio": false, "desde": null },
  "spo2": { "umbral": { "graveBajo": 88, "normalMin": 92, "normalMax": null, "graveAlto": null }, "propio": false, "desde": null },
  "zonaSegura": {
    "zona": { "latitud": -23.6509, "longitud": -70.3975, "radioMetros": 200, "severidad": "MEDIA" },
    "vigenteDesde": "2026-11-05T15:00:00Z"
  }
}
```
- `zonaSegura` es `null` si nunca se definió; viene aunque el módulo esté apagado.
- `minutosEspera` de afuera es el **vigente** (el propio o el base); el de `ajustes` es `null` si el paciente usa el base.
- `propio: false` indica que el umbral es el **valor base** (definido en `application.properties`; provisional hasta que lo valide el equipo clínico).

### `PUT …/perfil-clinico/ajustes`
El cuerpo es `ajustes` (como arriba), con los siete módulos. `motivo` hasta 500 caracteres. 400 si se activa `zonaSegura` sin `gpsActivo` o sin una zona definida.

### `PUT …/perfil-clinico/zona-segura`
```json
{ "latitud": -23.6509, "longitud": -70.3975, "radioMetros": 200, "severidad": "MEDIA" }
```
Define o cambia la zona (no la activa). `radioMetros` de 50 a 5000; `severidad` `LEVE`, `MEDIA` o `GRAVE` (vacía = `MEDIA`). Las coordenadas se guardan cifradas. Si es igual a la vigente no agrega una versión.

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

### `GET /api/pacientes/{id}/reloj/configuracion`
Si el reloj ya aplica la configuración vigente (contactos, umbrales, calendario), según la versión que informó en su último lote.
```json
{
  "estado": "AL_DIA",
  "versionVigente": "9f2c4e1a7b3d5e60",
  "versionReloj": "9f2c4e1a7b3d5e60",
  "aplicadaDesde": "2026-10-27T14:05:00Z",
  "ultimaSincronizacion": "2026-10-27T16:05:00Z"
}
```
- `estado`: `AL_DIA`, `PENDIENTE` (el reloj aún no descarga los últimos cambios), `SIN_SINCRONIZAR` (reloj recién entregado) o `SIN_RELOJ`.
- `aplicadaDesde`: primer lote de la racha actual con esa versión. Vacío si no ha sincronizado.
- Solo cuenta el reloj vigente. 404 si el paciente no existe.

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

## Alertas

### `GET /api/alertas`
Hasta 200 alertas no canceladas: primero las `SIN_RESPUESTA` abiertas y después las más recientes. Las canceladas por el paciente no aparecen, porque solo sirven para métricas. El front todavía usa datos de ejemplo.
```json
[{
  "id": "…", "numero": 142,
  "paciente": { "id": "…", "nombres": "María", "apellidos": "Soto Rojas", "edad": 82 },
  "tipoEvento": "CAIDA", "severidad": "GRAVE",
  "estadoContacto": "SIN_RESPUESTA", "estadoAtencion": "SIN_ASIGNAR",
  "responsable": null, "recepcion": "2026-11-11T15:00:00Z", "prioridadMaxima": true
}]
```
`severidad` es la actual: `GRAVE` si se agravó. `prioridadMaxima` es `true` si nadie de la lista confirmó y la alerta sigue abierta.

### `GET /api/alertas/{id}`
Los mismos campos de la lista, más `atencion` (`null` si sigue abierta; si no, `fechaHora`, `resolutor`, `clasificacion` y `observaciones`) y `lineaTiempo`, del paso más antiguo al más reciente:
```json
{ "fechaHora": "…", "tipo": "AVISO_ENVIADO", "contacto": "Hija Ana", "canal": "WHATSAPP", "ronda": 1, "detalle": null, "usuario": null }
```
- `tipo`: `ALERTA_RECIBIDA`, `ALERTA_AGRAVADA`, `ALERTA_CANCELADA`, `SALTO_CONTACTO`, `AVISO_ENVIADO`, `AVISO_ENTREGADO`, `AVISO_LEIDO`, `AVISO_FALLIDO`, `RESPUESTA_CONTACTO`, `SIN_RESPUESTA`, `CASO_ASUMIDO`, `SEGUIMIENTO`, `ALERTA_RESUELTA`, `SEGUIMIENTO_CERRADO`, `FCM_PENDIENTE`, `FCM_ENVIADO`, `FCM_RECIBIDO` o `FCM_NO_CONFIRMADO`.
- `detalle`: el motivo del fallo, la respuesta del contacto, el tipo de aviso al reloj, la clasificación o la nota de seguimiento.
- Las fechas son las registradas: lo que informa el reloj usa la hora del reloj, así que una cancelación aparece antes que la recepción.

### `POST /api/alertas/{id}/asumir`
Sin cuerpo. La persona queda a cargo. 409 si otra ya la tomó, si está cerrada o si el paciente la canceló.

### `POST /api/alertas/{id}/resolver`
```json
{ "clasificacion": "POSITIVA", "observaciones": "Se llamó a la hija" }
```
- `observaciones` es obligatorio siempre.
- `clasificacion` (`POSITIVA` o `FALSA_ALARMA`) es obligatoria en las alertas de salud y debe ir vacía en las técnicas.
- La puede resolver cualquier cuenta con el permiso. Si nadie la había asumido, quien resuelve queda como responsable.
- Una `POSITIVA` queda `EN_SEGUIMIENTO`.
- 409 si ya está cerrada o si el paciente la canceló.

### `POST /api/alertas/{id}/seguimiento/cerrar`
Sin cuerpo. Termina el seguimiento de una `POSITIVA`. 409 si la alerta no tiene uno abierto.

## Todavía no existen

El front no debe llamarlos aún. Si los necesita, puede usar datos de ejemplo con el mismo formato, como hace hoy la página de inicio.

| Qué | Para | Cronograma |
|---|---|---|
| Panel de estado general (última sincronización, estado del reloj) | Página de inicio | 3.4.1 |
| Tendencias de FC y SpO2, ubicaciones | Gráficos en la ficha | 3.5 |
| Reportes diarios y semanales | Reportes | 3.6 |
| Alertas pendientes de día hábil | Bandeja | 3.7 |
| Estado técnico de los relojes (batería, versión, conexión, plan por vencer) | Inventario | 3.8 |
| Cierre de sesión y renovación del token | Sesión | 1.3.4 |
| Listado y edición de cuentas del equipo | Administración | sin tarea aún |
