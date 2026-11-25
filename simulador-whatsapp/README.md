# Simulador de contactos de WhatsApp

Programa que se hace pasar por la Cloud API de Meta y por los contactos que reciben los avisos (tarea 0.9 del cronograma). Sirve para probar el escalamiento sin enviar mensajes reales ni gastar conversaciones de Meta.

## Qué hace

1. Recibe los envíos del backend en `POST /{versión}/{id del número}/messages`, igual que la Cloud API (`https://graph.facebook.com/v21.0/{id}/messages`). Responde como Meta: `200` con el id del mensaje (`wamid.…`), `401` si falta el token o no es el configurado, y `400` si el envío no tiene destinatario o no es de WhatsApp.
2. Después manda al webhook del backend (por defecto `http://localhost:8080/api/webhooks/whatsapp`) lo que pasaría en Meta, según el **último dígito del número** de destino:

| Dígito | Qué llega al webhook |
|---|---|
| 1, 0 y 5 a 9 | enviado, entregado, leído y botón **"Voy en camino"** |
| 2 | enviado, entregado, leído y botón **"No puedo"** |
| 3 | enviado, entregado y leído; el contacto **no responde** |
| 4 | el mensaje **falla** (estado `failed` con el error 131026 de Meta) |

Enviado y entregado llegan al segundo; leído y la respuesta, tras la demora (5 s, se cambia con `--demora`).

Cada webhook va firmado como lo hace Meta: cabecera `X-Hub-Signature-256: sha256=<HMAC-SHA256 del cuerpo con el secreto de la app>`. El backend debe usar el mismo secreto que se le da al simulador con `--secreto`.

### Cómo llega la respuesta del contacto

- **Plantilla** (lo normal: los avisos se envían con plantillas aprobadas): el mensaje llega con `type: "button"` y `button.payload`. El payload es el que el backend puso en ese botón al enviar (`components` de tipo `button`, `index` 0 para "Voy en camino" y 1 para "No puedo"); si no puso ninguno, es el texto del botón, como en Meta.
- **Mensaje interactivo con botones**: llega con `type: "interactive"` e `interactive.button_reply` (`id` y `title` del botón).
- **Texto simple**: no tiene botones, así que solo llegan los estados.

En todos los casos `context.id` es el id del mensaje original, que es lo que vincula la respuesta con el intento.

## Uso

Requisitos: Node 18 o superior. No tiene dependencias que instalar.

```sh
node simulador-whatsapp/simulador.mjs --secreto <secreto de la app>
```

| Opción | Para qué |
|---|---|
| `--secreto <s>` | Secreto con que se firman los webhooks. Obligatorio. |
| `--webhook <url>` | Webhook del backend. Por defecto `http://localhost:8080/api/webhooks/whatsapp`. |
| `--puerto <n>` | Puerto donde recibe los envíos. Por defecto `9091` (el `9090` es del simulador de reloj). |
| `--token <t>` | Token de acceso que debe traer cada envío. Sin esta opción acepta cualquiera. |
| `--demora <s>` | Segundos hasta que el contacto lee y responde. Por defecto `5`. Subirla sirve para ver al reloj pasar al siguiente contacto. |

Cada evento se anota con la hora local, el número y el final del id del mensaje.

Para probarlo a mano sin el backend:

```sh
curl -X POST localhost:9091/v21.0/123/messages -H "Authorization: Bearer x" -H "Content-Type: application/json" \
  -d '{"messaging_product":"whatsapp","to":"56912345671","type":"template","template":{"name":"alerta_leve","language":{"code":"es"}}}'
```

**Con el backend (desde el 24-11-2026):** en el `.env` poner `WHATSAPP_CANAL=SIMULADOR` (los demás `WHATSAPP_*` de `.env.example` sirven tal cual), levantar con `docker compose up -d` y correr el simulador con el mismo secreto y el webhook del backend:

```bash
node simulador-whatsapp/simulador.mjs --secreto secreto-del-simulador --token token-del-simulador
```

El backend le envía las plantillas a `WHATSAPP_SIMULADOR_URL` (`http://host.docker.internal:9091` desde Docker) y el simulador responde por `/api/webhooks/whatsapp`. En la ficha de la alerta se ven el envío, la entrega, la lectura y la respuesta.

## Lo que no imita

- Meta reintenta un webhook que falla; el simulador no.
- No hace la verificación inicial del webhook (`GET` con `hub.challenge`), que Meta hace una sola vez al configurarlo.
- No revisa que la plantilla exista ni que esté aprobada.

## Pruebas

```sh
node --test simulador-whatsapp/
```

Levantan un webhook falso y revisan cada dígito, el orden y la demora de los estados, el formato de Meta, la firma y los errores de envío.
