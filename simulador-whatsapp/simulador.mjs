#!/usr/bin/env node
/**
 * Simulador de contactos de WhatsApp.
 *
 * Se hace pasar por la Cloud API de Meta y por los contactos que reciben los mensajes. Acepta los envíos
 * del backend (POST /{versión}/{id del número}/messages) y después manda al webhook del backend, firmados
 * como lo hace Meta, los estados de entrega y la respuesta del contacto. Lo que pasa depende del último
 * dígito del número de destino:
 *
 * | Dígito      | Qué hace el contacto                                   |
 * |-------------|--------------------------------------------------------|
 * | 1, 0, 5 a 9 | Lee el mensaje y aprieta "Voy en camino"                |
 * | 2           | Lee el mensaje y aprieta "No puedo"                     |
 * | 3           | Lee el mensaje y no responde                            |
 * | 4           | El mensaje falla (no se entrega)                        |
 *
 * Enviado y entregado llegan al segundo; leído y la respuesta, tras la demora (5 s por defecto).
 * Uso y opciones: ver README.md de esta carpeta o `node simulador-whatsapp/simulador.mjs --ayuda`.
 * Sin dependencias: solo Node 18 o superior.
 */
import { createServer } from 'node:http';
import { createHmac, randomBytes } from 'node:crypto';
import { parseArgs } from 'node:util';
import { pathToFileURL } from 'node:url';

const AYUDA = `Simulador de contactos de WhatsApp: imita la Cloud API de Meta y responde al webhook.

Uso:
  node simulador-whatsapp/simulador.mjs --secreto <secreto de la app> [opciones]

Opciones:
  --secreto <s>      secreto de la app de Meta con que se firman los webhooks (el mismo que usa el backend)
  --webhook <url>    webhook del backend (por defecto http://localhost:8080/api/webhooks/whatsapp)
  --puerto <n>       puerto donde recibe los envíos (por defecto 9091)
  --token <t>        token de acceso que debe traer cada envío; sin esta opción acepta cualquiera
  --demora <s>       segundos hasta que el contacto lee y responde (por defecto 5)
  --ayuda            muestra esta ayuda

Qué hace el contacto, según el último dígito del número:
  1, 0, 5 a 9  lee y aprieta "Voy en camino"
  2            lee y aprieta "No puedo"
  3            lee y no responde
  4            el mensaje falla`;

/** Los dos botones de respuesta rápida, en el orden de las plantillas: primero confirma, después rechaza. */
const BOTONES = [
	{ indice: '0', texto: 'Voy en camino' },
	{ indice: '1', texto: 'No puedo' },
];

/** Error que informa Meta cuando un mensaje no se puede entregar. */
const ERROR_NO_ENTREGABLE = {
	code: 131026,
	title: 'Message undeliverable',
	message: 'Message undeliverable',
	error_data: { details: 'Mensaje fallido simulado (número terminado en 4).' },
};

/** Ruta del envío de la Cloud API: /v{versión}/{id del número}/messages. */
const RUTA_ENVIO = /^\/v\d+(?:\.\d+)?\/([^/]+)\/messages$/;

/**
 * Qué hace el contacto según el último dígito de su número.
 * @param {string} numero solo dígitos
 * @returns {'CONFIRMA' | 'NO_PUEDE' | 'NO_RESPONDE' | 'FALLA'} el comportamiento
 */
export function comportamiento(numero) {
	switch (numero.at(-1)) {
		case '2':
			return 'NO_PUEDE';
		case '3':
			return 'NO_RESPONDE';
		case '4':
			return 'FALLA';
		default:
			return 'CONFIRMA';
	}
}

/**
 * Id de mensaje con la forma de los de Meta.
 * @returns {string} por ejemplo "wamid.SIM…"
 */
function nuevoIdMensaje() {
	return `wamid.SIM${randomBytes(12).toString('hex').toUpperCase()}`;
}

/**
 * Hora actual como la manda Meta: segundos desde 1970, en texto.
 * @returns {string} la marca de tiempo
 */
function marcaDeTiempo() {
	return String(Math.floor(Date.now() / 1000));
}

/**
 * Firma un cuerpo como Meta: "sha256=" + HMAC-SHA256 en hexadecimal con el secreto de la app.
 * @param {string} secreto secreto de la app
 * @param {string} cuerpo el JSON tal cual se envía
 * @returns {string} el valor de la cabecera X-Hub-Signature-256
 */
export function firmar(secreto, cuerpo) {
	return `sha256=${createHmac('sha256', secreto).update(cuerpo).digest('hex')}`;
}

/**
 * Respuesta de error con el formato de la Graph API.
 * @param {import('node:http').ServerResponse} respuesta la respuesta HTTP
 * @param {number} estado código HTTP
 * @param {number} codigo código de error de Meta (100 parámetro inválido, 190 token inválido)
 * @param {string} mensaje descripción del error
 */
function responderError(respuesta, estado, codigo, mensaje) {
	const tipo = codigo === 190 ? 'OAuthException' : 'GraphMethodException';
	responderJson(respuesta, estado, { error: { message: mensaje, type: tipo, code: codigo, fbtrace_id: 'SIMULADOR' } });
}

/**
 * Responde un JSON.
 * @param {import('node:http').ServerResponse} respuesta la respuesta HTTP
 * @param {number} estado código HTTP
 * @param {object} cuerpo el JSON
 */
function responderJson(respuesta, estado, cuerpo) {
	respuesta.writeHead(estado, { 'Content-Type': 'application/json' }).end(JSON.stringify(cuerpo));
}

/**
 * Lee el cuerpo de un pedido como texto.
 * @param {import('node:http').IncomingMessage} pedido el pedido
 * @returns {Promise<string>} el cuerpo
 */
async function leerCuerpo(pedido) {
	const partes = [];
	for await (const parte of pedido) {
		partes.push(parte);
	}
	return Buffer.concat(partes).toString('utf8');
}

/**
 * Revisa que el envío tenga lo mínimo que exige Meta.
 * @param {any} envio el cuerpo leído
 * @returns {string | null} el problema, o null si está bien
 */
function problemaDelEnvio(envio) {
	if (envio === null || typeof envio !== 'object') {
		return 'El cuerpo debe ser un objeto JSON.';
	}
	if (envio.messaging_product !== 'whatsapp') {
		return 'messaging_product debe ser "whatsapp".';
	}
	if (typeof envio.to !== 'string' || envio.to.replace(/\D/g, '') === '') {
		return 'Falta el destinatario (to).';
	}
	return null;
}

/**
 * Lo que aprieta el contacto, según el tipo del mensaje que recibió. Con una plantilla, Meta manda
 * `type: "button"` con el payload que puso el backend en ese botón (o el texto del botón si no puso
 * ninguno); con un mensaje interactivo, `type: "interactive"` con el id y título del botón. Un texto
 * simple no tiene botones.
 * @param {any} envio el mensaje que se envió
 * @param {number} posicion 0 = "Voy en camino", 1 = "No puedo"
 * @returns {object | null} los campos del mensaje de respuesta, o null si no hay botón que apretar
 */
function respuestaDelBoton(envio, posicion) {
	if (envio.type === 'template') {
		const boton = BOTONES[posicion];
		const componente = (envio.template?.components ?? [])
			.find((c) => c.type === 'button' && String(c.index) === boton.indice);
		const payload = componente?.parameters?.find((p) => p.type === 'payload')?.payload ?? boton.texto;
		return { type: 'button', button: { payload, text: boton.texto } };
	}
	if (envio.type === 'interactive' && envio.interactive?.type === 'button') {
		const boton = envio.interactive.action?.buttons?.[posicion]?.reply;
		return boton ? { type: 'interactive', interactive: { type: 'button_reply', button_reply: { id: boton.id, title: boton.title } } } : null;
	}
	return null;
}

/**
 * Arma un webhook de Meta con un solo cambio en el campo "messages".
 * @param {string} idNumero id del número de WhatsApp que envió
 * @param {object} contenido `statuses` o `contacts` + `messages`
 * @returns {object} el cuerpo del webhook
 */
function webhookDeMeta(idNumero, contenido) {
	return {
		object: 'whatsapp_business_account',
		entry: [{
			id: 'SIMULADOR-WABA',
			changes: [{
				field: 'messages',
				value: {
					messaging_product: 'whatsapp',
					metadata: { display_phone_number: '56900000000', phone_number_id: idNumero },
					...contenido,
				},
			}],
		}],
	};
}

/**
 * Webhook con el estado de un mensaje.
 * @param {object} mensaje el mensaje enviado ({id, numero, idNumero})
 * @param {'sent' | 'delivered' | 'read' | 'failed'} estado el estado
 * @returns {object} el cuerpo del webhook
 */
function webhookDeEstado(mensaje, estado) {
	return webhookDeMeta(mensaje.idNumero, {
		statuses: [{
			id: mensaje.id,
			status: estado,
			timestamp: marcaDeTiempo(),
			recipient_id: mensaje.numero,
			...(estado === 'failed' ? { errors: [ERROR_NO_ENTREGABLE] } : {}),
		}],
	});
}

/**
 * Webhook con el botón que apretó el contacto.
 * @param {object} mensaje el mensaje enviado ({id, numero, idNumero})
 * @param {object} boton lo que devolvió {@link respuestaDelBoton}
 * @returns {object} el cuerpo del webhook
 */
function webhookDeBoton(mensaje, boton) {
	return webhookDeMeta(mensaje.idNumero, {
		contacts: [{ profile: { name: `Contacto ${mensaje.numero.slice(-4)}` }, wa_id: mensaje.numero }],
		messages: [{
			from: mensaje.numero,
			id: nuevoIdMensaje(),
			timestamp: marcaDeTiempo(),
			context: { from: '56900000000', id: mensaje.id },
			...boton,
		}],
	});
}

/**
 * Levanta el simulador.
 * @param {object} opciones
 * @param {number} opciones.puerto puerto donde recibe los envíos (0 = uno libre cualquiera)
 * @param {string} opciones.webhook URL del webhook del backend
 * @param {string} opciones.secreto secreto con que se firman los webhooks
 * @param {string} [opciones.token] token de acceso exigido; sin él acepta cualquiera
 * @param {number} opciones.demoraMs milisegundos hasta que el contacto lee y responde
 * @param {number} [opciones.esperaEstadosMs] milisegundos hasta los estados enviado y entregado (1000)
 * @param {boolean} [opciones.silencioso] no escribe en la consola
 * @returns {Promise<{url: string, cerrar: () => Promise<void>}>} dirección del simulador y cómo cerrarlo
 */
export async function iniciarSimulador(opciones) {
	const esperaEstadosMs = opciones.esperaEstadosMs ?? 1000;
	const temporizadores = new Set();
	const anotar = opciones.silencioso ? () => {} : (texto) => {
		const hora = new Date().toLocaleTimeString('es-CL', { hour12: false });
		console.log(`${hora}  ${texto}`);
	};

	/**
	 * Hace algo después de un rato, sin impedir que el simulador se cierre.
	 * @param {number} ms milisegundos
	 * @param {() => Promise<void>} tarea lo que se hace
	 */
	function despues(ms, tarea) {
		const temporizador = setTimeout(() => {
			temporizadores.delete(temporizador);
			tarea().catch((e) => anotar(`error: ${e.message}`));
		}, ms);
		temporizadores.add(temporizador);
	}

	/**
	 * Manda un cuerpo al webhook del backend, firmado. Si el backend no contesta, lo anota y sigue (Meta
	 * reintentaría; el simulador no).
	 * @param {object} cuerpo el webhook
	 * @param {string} descripcion lo que se anota
	 */
	async function avisarAlWebhook(cuerpo, descripcion) {
		const texto = JSON.stringify(cuerpo);
		try {
			const respuesta = await fetch(opciones.webhook, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json', 'X-Hub-Signature-256': firmar(opciones.secreto, texto) },
				body: texto,
			});
			anotar(`${descripcion} → webhook ${respuesta.status}`);
		} catch (e) {
			anotar(`${descripcion} → el webhook no contestó (${e.cause?.code ?? e.message})`);
		}
	}

	/**
	 * Programa lo que hace el contacto con un mensaje recién aceptado.
	 * @param {object} mensaje {id, numero, idNumero}
	 * @param {any} envio el mensaje que se envió
	 */
	function programar(mensaje, envio) {
		const quien = `${mensaje.numero} (${mensaje.id.slice(-6)})`;
		const accion = comportamiento(mensaje.numero);
		if (accion === 'FALLA') {
			despues(esperaEstadosMs, () => avisarAlWebhook(webhookDeEstado(mensaje, 'failed'), `${quien}: falló`));
			return;
		}
		despues(esperaEstadosMs, async () => {
			await avisarAlWebhook(webhookDeEstado(mensaje, 'sent'), `${quien}: enviado`);
			await avisarAlWebhook(webhookDeEstado(mensaje, 'delivered'), `${quien}: entregado`);
		});
		despues(Math.max(opciones.demoraMs, esperaEstadosMs), async () => {
			await avisarAlWebhook(webhookDeEstado(mensaje, 'read'), `${quien}: leído`);
			if (accion === 'NO_RESPONDE') {
				anotar(`${quien}: no responde`);
				return;
			}
			const boton = respuestaDelBoton(envio, accion === 'CONFIRMA' ? 0 : 1);
			if (boton) {
				const nombre = boton.button?.text ?? boton.interactive.button_reply.title;
				await avisarAlWebhook(webhookDeBoton(mensaje, boton), `${quien}: apretó "${nombre}"`);
			}
		});
	}

	const servidor = createServer(async (pedido, respuesta) => {
		const ruta = RUTA_ENVIO.exec(new URL(pedido.url, 'http://simulador').pathname);
		if (pedido.method !== 'POST' || !ruta) {
			responderError(respuesta, 404, 100, 'Ruta desconocida para el simulador.');
			return;
		}
		const autorizacion = pedido.headers.authorization ?? '';
		const token = autorizacion.startsWith('Bearer ') ? autorizacion.slice(7).trim() : '';
		if (!token || (opciones.token && token !== opciones.token)) {
			responderError(respuesta, 401, 190, 'Invalid OAuth access token.');
			return;
		}
		let envio;
		try {
			envio = JSON.parse(await leerCuerpo(pedido));
		} catch {
			responderError(respuesta, 400, 100, 'El cuerpo no es JSON válido.');
			return;
		}
		const problema = problemaDelEnvio(envio);
		if (problema) {
			responderError(respuesta, 400, 100, problema);
			return;
		}
		const mensaje = { id: nuevoIdMensaje(), numero: envio.to.replace(/\D/g, ''), idNumero: ruta[1] };
		responderJson(respuesta, 200, {
			messaging_product: 'whatsapp',
			contacts: [{ input: envio.to, wa_id: mensaje.numero }],
			messages: [{ id: mensaje.id }],
		});
		const nombre = envio.template?.name ? `plantilla ${envio.template.name}` : envio.type;
		anotar(`${mensaje.numero}: llegó ${nombre}; el contacto ${comportamiento(mensaje.numero)}`);
		programar(mensaje, envio);
	});

	await new Promise((listo) => servidor.listen(opciones.puerto, listo));
	const url = `http://localhost:${servidor.address().port}`;
	anotar(`Simulador de WhatsApp en ${url} → webhook ${opciones.webhook} (Ctrl+C para salir)`);
	return {
		url,
		cerrar: () => {
			temporizadores.forEach(clearTimeout);
			temporizadores.clear();
			servidor.closeAllConnections();
			return new Promise((listo) => servidor.close(() => listo()));
		},
	};
}

/**
 * Lee las opciones de la línea de comandos.
 * @returns {object} las opciones para {@link iniciarSimulador}
 */
function leerOpciones() {
	const { values } = parseArgs({
		options: {
			secreto: { type: 'string' },
			webhook: { type: 'string', default: 'http://localhost:8080/api/webhooks/whatsapp' },
			puerto: { type: 'string', default: '9091' },
			token: { type: 'string' },
			demora: { type: 'string', default: '5' },
			ayuda: { type: 'boolean', default: false },
		},
	});
	const demora = Number(values.demora);
	if (values.ayuda || !values.secreto || !Number.isFinite(demora) || demora < 0) {
		console.log(AYUDA);
		process.exit(values.ayuda ? 0 : 1);
	}
	return {
		secreto: values.secreto,
		webhook: values.webhook,
		puerto: Number(values.puerto),
		token: values.token,
		demoraMs: demora * 1000,
	};
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
	iniciarSimulador(leerOpciones());
}
