# Simulador de reloj

Programa que se hace pasar por uno o varios relojes para probar la plataforma sin el reloj real (tarea 0.5 del cronograma).

**Hoy solo hace la parte de los avisos del servidor (0.5.6):** registra el token FCM de cada reloj, recibe los avisos (`ALERTA_CONFIRMADA`, `ALERTA_RESUELTA`, `SIMULACRO`, `CONFIGURACION_NUEVA`), acusa los que se acusan y descarga la configuración cuando cambia. Las sincronizaciones, las alertas y el escalamiento (0.5.2 a 0.5.5 y 0.5.7) todavía no están.

## Cómo recibe los avisos

Un script no puede recibir mensajes de Firebase: Firebase solo los entrega a apps Android, iOS o a un navegador. Por eso el backend tiene un canal propio para el simulador. Con `FCM_CANAL=SIMULADOR`, en vez de enviar a Firebase, hace un `POST` a `FCM_SIMULADOR_URL` (por defecto `http://localhost:9090/avisos`) con el token del reloj y los mismos datos que irían por Firebase:

```json
{ "token": "sim-1-…", "datos": { "tipo": "ALERTA_RESUELTA", "idAviso": "…", "eventIdAlerta": "…" } }
```

El simulador responde como Firebase: 204 si lo aceptó y 410 si no conoce ese reloj, y entonces el backend olvida el token. Lo demás (acuse, configuración) usa la API normal del reloj (`docs/endpoints-reloj.md`).

## Uso

Requisitos: Node 18 o superior. No tiene dependencias que instalar.

1. Levantar el backend con el canal del simulador:
   - Con Maven: `FCM_CANAL=SIMULADOR ./mvnw -pl app spring-boot:run -Dspring-boot.run.profiles=dev`
   - Con Docker: poner `FCM_CANAL=SIMULADOR` en `.env` y `docker compose up -d --build`. El backend llega al simulador por `host.docker.internal`.
2. En el dashboard, vincular un reloj a un paciente y copiar el token del reloj (se muestra una sola vez).
3. Desde la raíz del repositorio:

```sh
node simulador-reloj/simulador.mjs --token <token del reloj>
```

| Opción | Para qué |
|---|---|
| `--token <t>` | Token del reloj. Se puede repetir para simular varios relojes a la vez. |
| `--backend <url>` | Dirección del backend. Por defecto `http://localhost:8080`. |
| `--puerto <n>` | Puerto donde escucha. Por defecto `9090`; si se cambia, cambiar también `FCM_SIMULADOR_URL` (o `SIM_PUERTO` en `.env` con Docker). |
| `--sin-acuse` | No acusa los avisos, para ver los reintentos del servidor (a 1, 1 y 2 minutos). |
| `--rechazar-token` | Responde 410 a todo aviso, para ver que el servidor deja al reloj sin token. |

Cada aviso se muestra con la hora local. Si un mismo `idAviso` llega otra vez (un reintento), se marca `REPETIDO`.

## Qué probar

- **Alerta resuelta:** enviar una alerta con el token del reloj y resolverla desde el dashboard. Llega `ALERTA_RESUELTA`, el simulador la acusa, y la línea de tiempo de la alerta muestra `FCM_ENVIADO` y `FCM_RECIBIDO`.
- **Sin acuse:** lo mismo con `--sin-acuse`. El aviso llega 4 veces (envío y 3 reintentos) y la línea de tiempo termina en `FCM_NO_CONFIRMADO`.
- **Configuración nueva:** cambiar un contacto del paciente. Llega `CONFIGURACION_NUEVA` y el simulador descarga la configuración.
- **Token rechazado:** con `--rechazar-token`, el backend deja al reloj sin token. El aviso espera hasta que el reloj registre uno nuevo (volver a correr el simulador sin la opción).
