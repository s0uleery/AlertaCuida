# Endpoints del reloj

Lo que el backend **ya atiende** para el reloj (o el simulador), al 26 de octubre de 2026. Es una referencia rápida con ejemplos. El contrato completo, que incluye lo que todavía no está hecho, está en [api-reloj.md](api-reloj.md) (decisiones y flujo) y en [api-reloj.yaml](api-reloj.yaml) (OpenAPI). Los endpoints del dashboard van aparte, en [endpoints-dashboard.md](endpoints-dashboard.md).

## Reglas comunes

- **Base:** `/api/reloj/v1`. En desarrollo, `http://localhost:8080/api/reloj/v1`. El subdominio público está por definir con TI.
- **Token del reloj:** cada petición lleva `Authorization: Bearer <token>`.
  - El token se obtiene al vincular el reloj con un paciente desde el dashboard (`POST /api/pacientes/{id}/reloj`) y se muestra una sola vez.
  - Con él, el servidor sabe qué reloj es y de qué paciente: el reloj **nunca** envía el id del paciente.
  - El token del dashboard no sirve aquí, ni el del reloj en el dashboard.
- **Horas:** siempre en UTC, ISO 8601 (`2026-10-26T14:00:00Z`).
- **`eventId`:** cada mensaje lleva un UUID v4 que genera el reloj. Enviar el mismo `eventId` otra vez no duplica nada y devuelve la misma respuesta. Por eso el reloj debe guardar el mensaje en su cola **antes** de enviarlo y reintentarlo con el mismo `eventId` hasta recibir un 2xx.
- **Errores:** formato RFC 9457 con un campo `codigo` estable, para que el reloj decida sin leer el texto:
  ```json
  { "title": "Not Found", "status": 404, "detail": "…", "codigo": "ALERTA_DESCONOCIDA" }
  ```

| Estado | `codigo` | Qué hacer |
|---|---|---|
| 400 | `MENSAJE_INVALIDO` | Falta un campo o un valor no existe: es un error de programación del reloj. No reintentar; si era una alerta, mostrar "No se pudo avisar". |
| 401 | `TOKEN_INVALIDO` | Token ausente, desconocido o revocado (reloj desvinculado o reemplazado). Dejar de enviar y mostrar que el reloj está desvinculado. |
| 404 | `ALERTA_DESCONOCIDA` | El servidor no tiene esa alerta. Reenviar primero la alerta y después repetir el mensaje. |
| 409 | `NO_APLICA` | El `eventId` ya se usó para otra cosa (por ejemplo, se envió como alerta y ahora llega como cancelación). No reintentar. |
| 413 | `LOTE_GRANDE` | Más de 2.000 lecturas o ubicaciones. Partir el historial en varios lotes, cada uno con su `eventId`. |
| 429 / 5xx / sin red | — | Reintentar más tarde (respetar `Retry-After` si viene). |

## Resumen

| Método y ruta | Cuándo | Respuesta |
|---|---|---|
| `POST /api/reloj/v1/sincronizaciones` | Cada 2 h, o al bajar del 15 % de batería | 200 |
| `POST /api/reloj/v1/alertas` | Al terminar la ventana de 30 s sin cancelación | 201 nueva, 200 reenvío |
| `POST /api/reloj/v1/cancelaciones` | Si el paciente canceló dentro de los 30 s (solo métricas) | 201 nueva, 200 reenvío |
| `GET /api/reloj/v1/alertas/{eventId}` | Antes de pasar al siguiente contacto | 200 |
| `GET /api/reloj/v1/configuracion` | Si la sincronización responde `configuracionNueva: true` | 200, 304 si no cambió |
| `PUT /api/reloj/v1/token-fcm` | Al instalarse y cada vez que Firebase renueva el token | 204 |

## `POST /api/reloj/v1/sincronizaciones`

Un solo JSON con el estado del reloj y todo lo acumulado desde la sincronización anterior.

```json
{
  "eventId": "3b0e6f3c-1f9a-4d55-9d0e-6a8f2f1c7a10",
  "fechaHoraEnvio": "2026-10-26T14:00:00Z",
  "estado": {
    "nivelBateria": 62,
    "relojPuesto": true,
    "minutosPuesto": 115,
    "tipoConexion": "LTE",
    "versionApp": "1.0.3",
    "versionConfiguracion": null
  },
  "lecturas": [
    { "tipo": "FC",   "valor": 78, "fechaHoraMedicion": "2026-10-26T12:30:00Z" },
    { "tipo": "SPO2", "valor": 96, "fechaHoraMedicion": "2026-10-26T12:30:00Z" }
  ],
  "ubicaciones": [
    { "latitud": -23.6509, "longitud": -70.3975, "precisionMetros": 12, "fechaHoraMedicion": "2026-10-26T13:00:00Z" }
  ],
  "eventosDescartados": [
    { "tipoEvento": "CAIDA", "cantidad": 1 }
  ]
}
```

| Campo | Obligatorio | Valores |
|---|---|---|
| `eventId`, `fechaHoraEnvio` | sí | |
| `estado.nivelBateria` | sí | 0 a 100 |
| `estado.relojPuesto` | sí | |
| `estado.minutosPuesto` | no | minutos puesto desde el lote anterior |
| `estado.tipoConexion` | sí | `LTE` o `WIFI` |
| `estado.versionApp` | sí | hasta 40 caracteres |
| `estado.versionConfiguracion` | no | hash de la configuración aplicada; `null` si aún no recibió ninguna |
| `lecturas` | no | `tipo`: `FC` (válida de 20 a 250) o `SPO2` (de 50 a 100) |
| `ubicaciones` | no | solo con el GPS activo |
| `eventosDescartados` | no | eventos detectados que no llegaron a alerta, por tipo |

Respuesta 200:
```json
{
  "idSincronizacion": "…",
  "fechaHoraRecepcion": "2026-10-26T14:00:02Z",
  "lecturasGuardadas": 1,
  "lecturasRepetidas": 0,
  "lecturasRechazadas": [ { "indice": 1, "motivo": "FUERA_DE_RANGO" } ],
  "loteRepetido": false,
  "configuracionNueva": false,
  "versionConfiguracion": "9f2c4e1a7b3d5e60"
}
```
- **Una lectura mala no rechaza el lote:** se descarta solo esa y aparece en `lecturasRechazadas`, con su posición en `lecturas` (desde 0). Motivos: `FUERA_DE_RANGO`, `HORA_FUTURA`, `ANTERIOR_A_LA_ASIGNACION`.
- `lecturasRepetidas`: lecturas que ya habían llegado en otro lote. No es un error.
- `loteRepetido: true`: ese `eventId` ya había llegado. La respuesta repite los contadores de la primera vez.
- `desfaseRelojSegundos`: solo aparece si la hora del reloj difiere en más de 120 s de la del servidor.
- **`configuracionNueva: true`** si la versión que informó el reloj (`estado.versionConfiguracion`) no es la vigente, o si no informó ninguna: el reloj debe descargarla con `GET /configuracion`. `versionConfiguracion` trae la vigente. También en un lote repetido, con la versión de ahora.

## `POST /api/reloj/v1/alertas`

```json
{
  "eventId": "9d7c2a41-6b0e-4f3a-a1c2-5e8d7f6b4c21",
  "tipoEvento": "CAIDA",
  "severidad": "GRAVE",
  "versionConfiguracion": "a9f3…",
  "fechaHoraDeteccion": "2026-10-26T15:10:00Z",
  "fechaHoraEnvio": "2026-10-26T15:10:31Z",
  "esperaDiaHabil": false,
  "evidencia": {
    "lecturas": [ { "tipo": "FC", "valor": 128, "fechaHoraMedicion": "2026-10-26T15:09:00Z" } ],
    "nivelBateria": 54,
    "segundosInmovilidad": 60,
    "celdaMatriz": null,
    "ubicacion": { "latitud": -23.6509, "longitud": -70.3975, "precisionMetros": 15, "fechaHoraMedicion": "2026-10-26T15:10:00Z" }
  }
}
```

| Campo | Obligatorio | Valores |
|---|---|---|
| `tipoEvento` | sí | `CAIDA`, `FC`, `SPO2`, `FC_SPO2`, `TENDENCIA`, `INACTIVIDAD`, `BATERIA_BAJA`, `RELOJ_NO_PUESTO`. `SILENCIO_DATOS` no: ese lo detecta solo el servidor |
| `severidad` | sí | `LEVE`, `MEDIA`, `GRAVE` |
| `versionConfiguracion` | sí | la configuración con que se detectó |
| `esperaDiaHabil` | sí | `true` si es leve, fuera del horario hábil, y el reloj no avisará a nadie |
| `evidencia` | no | hasta 100 lecturas; `celdaMatriz` solo para `FC_SPO2` |

Respuesta 201 (o 200 si es un reenvío, con el mismo cuerpo):
```json
{
  "idAlerta": "…",
  "numero": 142,
  "estado": {
    "eventId": "9d7c2a41-6b0e-4f3a-a1c2-5e8d7f6b4c21",
    "estado": "RECIBIDA",
    "severidadActual": "GRAVE",
    "detenerEscalamiento": false,
    "confirmadaPor": null
  }
}
```
**Una alerta nunca se rechaza por su contenido:** si la evidencia no calza con la configuración, el servidor igual la guarda. Solo responde 400 si el mensaje está mal formado.

## `POST /api/reloj/v1/cancelaciones`

La alerta que el paciente canceló en los 30 s nunca se envió como alerta. Se informa después, cuando haya conexión, solo para métricas.

```json
{
  "eventId": "c2f0…",
  "tipoEvento": "CAIDA",
  "severidad": "MEDIA",
  "versionConfiguracion": "a9f3…",
  "fechaHoraDeteccion": "2026-10-26T15:10:00Z",
  "fechaHoraCancelacion": "2026-10-26T15:10:12Z",
  "evidencia": { "segundosInmovilidad": 0 }
}
```
La respuesta tiene el mismo formato que la de una alerta, con `estado: "CANCELADA"`. 409 `NO_APLICA` si ese `eventId` ya se había enviado como alerta.

## `GET /api/reloj/v1/alertas/{eventId}`

El reloj la consulta antes de avisar al siguiente contacto, para saber si alguien ya respondió.

```json
{
  "eventId": "9d7c2a41-6b0e-4f3a-a1c2-5e8d7f6b4c21",
  "estado": "CONFIRMADA",
  "severidadActual": "GRAVE",
  "detenerEscalamiento": true,
  "confirmadaPor": { "idContacto": "…", "fechaHora": "2026-10-26T15:14:05Z" }
}
```
- `estado`: `RECIBIDA`, `AVISANDO_CONTACTO`, `CONFIRMADA`, `SIN_RESPUESTA`, `RESUELTA`, `PENDIENTE_DIA_HABIL` o `CANCELADA`.
- **Si `detenerEscalamiento` es `true`** (confirmada, resuelta o cancelada), el reloj deja de avisar.
- 404 `ALERTA_DESCONOCIDA` si este reloj no envió una alerta con ese `eventId`.

## `GET /api/reloj/v1/configuracion`

Todo lo que el reloj necesita para evaluar y escalar sin conexión. Con `If-None-Match` y la versión que ya tiene, responde **304 sin cuerpo** si no cambió. La versión viaja también en la cabecera `ETag` (entre comillas).

```json
{
  "version": "9f2c4e1a7b3d5e60",
  "modulos": { "caidas": true, "fc": true, "spo2": true, "inactividad": true, "tendencia": false },
  "gpsActivo": false,
  "modoConservador": false,
  "umbrales": {
    "fc": { "graveBajo": 40, "normalMin": 50, "normalMax": 110, "graveAlto": 130 },
    "spo2": { "graveBajo": 88, "normalMin": 92 }
  },
  "reglas": { "versionMatriz": "PROVISIONAL" },
  "escalamiento": { "minutosEspera": 5, "rondas": 2 },
  "contactos": [
    { "idContacto": "…", "nombre": "Ana Rojas", "prioridad": 1, "horario": null, "canales": ["WHATSAPP", "CORREO"] },
    { "idContacto": "…", "nombre": "Seguridad Ciudadana", "prioridad": 2, "horario": { "inicio": "20:00", "fin": "08:00" }, "canales": ["CORREO"] }
  ],
  "calendario": {
    "zonaHoraria": "America/Santiago",
    "horarioHabil": [ { "dia": "LUNES", "inicio": "08:30", "fin": "17:30" } ],
    "feriados": [ "2026-11-01", "2026-12-08" ]
  }
}
```
- **Umbrales y espera**: los del paciente, o los valores base si no tiene ajustes.
- **Contactos**: solo los activos con algún canal que el servidor pueda usar hoy (WhatsApp con opt-in vigente, o correo), por prioridad. Sin número ni correo: el reloj pide los avisos por `idContacto`. `horario: null` = todo el día; si `inicio` > `fin`, cruza la medianoche.
- **Feriados**: del año en curso y el siguiente. Las horas van en hora de Chile.
- **`versionMatriz`**: `PROVISIONAL` hasta recibir el Anexo B.

## `PUT /api/reloj/v1/token-fcm`

```json
{ "token": "token-que-entrega-firebase" }
```
Responde 204. El nuevo token reemplaza al anterior. 400 `MENSAJE_INVALIDO` si viene vacío o supera 4.096 caracteres.

## Todavía no existen

Están definidos en el contrato ([api-reloj.yaml](api-reloj.yaml)), pero el servidor aún no los atiende.

| Método y ruta | Para qué | Cronograma |
|---|---|---|
| `POST /api/reloj/v1/alertas/{eventId}/agravamiento` | Caída leve o media que pasa a grave por inmovilidad | 2.1.3 |
| `POST /api/reloj/v1/avisos-fcm/{idAviso}/recepcion` | Acusar que llegó un aviso FCM | 2.3 |
| `POST /api/reloj/v1/alertas/{eventId}/avisos` | Pedir al servidor que avise a un contacto | 2.10 |
| `POST /api/reloj/v1/alertas/{eventId}/sin-respuesta` | Informar que se recorrió la lista dos veces sin confirmación | 2.10 |
| Avisos FCM (servidor → reloj) | Alerta confirmada o resuelta, simulacro, configuración nueva | 2.3 |
