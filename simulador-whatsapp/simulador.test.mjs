/**
 * Pruebas del simulador de contactos de WhatsApp.
 *
 * Salen de los requisitos, no del código: el "Listo cuando" del cronograma (el servidor envía mensajes al
 * simulador y este responde al webhook confirmando, rechazando o sin responder), la regla de dígitos
 * decidida (1 = "Voy en camino", 2 = "No puedo", 3 = no responde, 4 = mensaje fallido, 0 y 5 a 9
 * confirman), las decisiones del 16-11-2026 (enviado y entregado al segundo, respuesta tras la demora,
 * el 3 llega a leído sin botón) y el formato de Meta (envío en /{versión}/{id del número}/messages,
 * webhook `whatsapp_business_account` firmado con X-Hub-Signature-256).
 *
 * El webhook del backend todavía no existe, así que cada prueba levanta un webhook falso que guarda lo
 * que recibe. Correr con: node --test simulador-whatsapp/
 */
import { test, after, before } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createHmac } from 'node:crypto';
import { iniciarSimulador } from './simulador.mjs';

const SECRETO = 'secreto-de-prueba';
const TOKEN = 'token-de-prueba';
const ID_NUMERO = '123456789';
/** Demora corta de la respuesta del contacto, para que las pruebas no esperen 5 s. */
const DEMORA_MS = 150;
/** Espera corta antes de los estados enviado y entregado. */
const ESPERA_ESTADOS_MS = 20;
/** Lo suficiente para que pase todo lo que el simulador pudiera mandar. */
const MARGEN_MS = DEMORA_MS + 400;

/** Lo que llegó al webhook falso: cabeceras, cuerpo tal cual y cuerpo leído. */
const recibidos = [];
let webhook;
let urlWebhook;

before(async () => {
	webhook = createServer(async (pedido, respuesta) => {
		const partes = [];
		for await (const parte of pedido) {
			partes.push(parte);
		}
		const crudo = Buffer.concat(partes).toString('utf8');
		recibidos.push({ metodo: pedido.method, ruta: pedido.url, cabeceras: pedido.headers, crudo, json: JSON.parse(crudo) });
		respuesta.writeHead(200).end();
	});
	await new Promise((listo) => webhook.listen(0, listo));
	urlWebhook = `http://localhost:${webhook.address().port}/api/webhooks/whatsapp`;
});

after(() => webhook.close());

/**
 * Levanta un simulador para una prueba y lo cierra al terminar.
 * @param {import('node:test').TestContext} t la prueba
 * @param {object} [extra] opciones que cambian las de siempre
 * @returns {Promise<{url: string}>} el simulador levantado
 */
async function simulador(t, extra = {}) {
	const sim = await iniciarSimulador({
		puerto: 0,
		webhook: urlWebhook,
		secreto: SECRETO,
		token: TOKEN,
		demoraMs: DEMORA_MS,
		esperaEstadosMs: ESPERA_ESTADOS_MS,
		silencioso: true,
		...extra,
	});
	t.after(() => sim.cerrar());
	return sim;
}

/**
 * Pide un envío como lo haría el backend con la Cloud API de Meta.
 * @param {{url: string}} sim el simulador
 * @param {object} cuerpo el mensaje
 * @param {object} [cabeceras] cabeceras que reemplazan a las de siempre
 * @param {string} [ruta] ruta del envío
 * @returns {Promise<Response>} la respuesta del simulador
 */
function enviar(sim, cuerpo, cabeceras = {}, ruta = `/v21.0/${ID_NUMERO}/messages`) {
	return fetch(`${sim.url}${ruta}`, {
		method: 'POST',
		headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json', ...cabeceras },
		body: JSON.stringify(cuerpo),
	});
}

/**
 * Mensaje de plantilla con los dos botones de respuesta rápida y su payload, como lo enviará el backend.
 * @param {string} numero teléfono del contacto
 * @returns {object} el cuerpo del envío
 */
function plantilla(numero) {
	return {
		messaging_product: 'whatsapp',
		to: numero,
		type: 'template',
		template: {
			name: 'alerta_grave',
			language: { code: 'es' },
			components: [
				{ type: 'body', parameters: [{ type: 'text', text: 'Mary' }] },
				{ type: 'button', sub_type: 'quick_reply', index: '0', parameters: [{ type: 'payload', payload: 'VOY_EN_CAMINO' }] },
				{ type: 'button', sub_type: 'quick_reply', index: '1', parameters: [{ type: 'payload', payload: 'NO_PUEDO' }] },
			],
		},
	};
}

/**
 * Envía una plantilla y espera a que el simulador termine de avisar al webhook.
 * @param {import('node:test').TestContext} t la prueba
 * @param {string} numero teléfono del contacto
 * @returns {Promise<{idMensaje: string, eventos: object[]}>} el id de Meta y lo que llegó al webhook por él
 */
async function enviarYEsperar(t, numero) {
	const sim = await simulador(t);
	const respuesta = await enviar(sim, plantilla(numero));
	assert.equal(respuesta.status, 200);
	const idMensaje = (await respuesta.json()).messages[0].id;
	await esperar(MARGEN_MS);
	return { idMensaje, eventos: eventosDe(idMensaje) };
}

/**
 * Espera un rato.
 * @param {number} ms milisegundos
 * @returns {Promise<void>} cuando pasa el tiempo
 */
function esperar(ms) {
	return new Promise((listo) => setTimeout(listo, ms));
}

/**
 * Los estados y respuestas que llegaron al webhook por un mensaje, en orden de llegada.
 * @param {string} idMensaje el id que devolvió el envío
 * @returns {object[]} `{tipo: 'estado', valor}` o `{tipo: 'boton', valor}` con el elemento original
 */
function eventosDe(idMensaje) {
	const eventos = [];
	for (const { json } of recibidos) {
		for (const entrada of json.entry ?? []) {
			for (const cambio of entrada.changes ?? []) {
				for (const estado of cambio.value.statuses ?? []) {
					if (estado.id === idMensaje) {
						eventos.push({ tipo: 'estado', valor: estado.status, elemento: estado, valorCambio: cambio.value });
					}
				}
				for (const mensaje of cambio.value.messages ?? []) {
					if (mensaje.context?.id === idMensaje) {
						eventos.push({ tipo: 'boton', valor: mensaje.button?.payload, elemento: mensaje, valorCambio: cambio.value });
					}
				}
			}
		}
	}
	return eventos;
}

/**
 * Resumen corto de los eventos, para comparar la secuencia completa.
 * @param {object[]} eventos los de {@link eventosDe}
 * @returns {string[]} por ejemplo ['sent', 'delivered', 'read', 'boton:VOY_EN_CAMINO']
 */
function secuencia(eventos) {
	return eventos.map((e) => (e.tipo === 'estado' ? e.valor : `boton:${e.valor}`));
}

test('número terminado en 1: enviado, entregado, leído y botón "Voy en camino"', async (t) => {
	const { eventos } = await enviarYEsperar(t, '56911111111');
	assert.deepEqual(secuencia(eventos), ['sent', 'delivered', 'read', 'boton:VOY_EN_CAMINO']);
	assert.equal(eventos.at(-1).elemento.button.text, 'Voy en camino');
});

test('número terminado en 2: enviado, entregado, leído y botón "No puedo"', async (t) => {
	const { eventos } = await enviarYEsperar(t, '56922222222');
	assert.deepEqual(secuencia(eventos), ['sent', 'delivered', 'read', 'boton:NO_PUEDO']);
	assert.equal(eventos.at(-1).elemento.button.text, 'No puedo');
});

test('número terminado en 3: lo lee y no responde', async (t) => {
	const { eventos } = await enviarYEsperar(t, '56933333333');
	assert.deepEqual(secuencia(eventos), ['sent', 'delivered', 'read']);
});

test('número terminado en 4: el mensaje falla, sin entregado ni respuesta', async (t) => {
	const { eventos } = await enviarYEsperar(t, '56944444444');
	assert.deepEqual(secuencia(eventos), ['failed']);
	const fallo = eventos[0].elemento;
	assert.ok(Array.isArray(fallo.errors) && fallo.errors.length > 0, 'el estado failed trae el error de Meta');
	assert.equal(typeof fallo.errors[0].code, 'number');
});

for (const digito of ['0', '5', '6', '7', '8', '9']) {
	test(`número terminado en ${digito}: confirma con "Voy en camino"`, async (t) => {
		const { eventos } = await enviarYEsperar(t, `5698765432${digito}`);
		assert.deepEqual(secuencia(eventos), ['sent', 'delivered', 'read', 'boton:VOY_EN_CAMINO']);
	});
}

test('enviado y entregado llegan pronto; la respuesta espera la demora', async (t) => {
	const sim = await simulador(t);
	const inicio = Date.now();
	const respuesta = await enviar(sim, plantilla('56911112221'));
	const idMensaje = (await respuesta.json()).messages[0].id;
	await esperar(DEMORA_MS / 2);
	assert.deepEqual(secuencia(eventosDe(idMensaje)), ['sent', 'delivered'], 'antes de la demora solo llegan esos dos');
	await esperar(MARGEN_MS);
	assert.ok(Date.now() - inicio >= DEMORA_MS);
	assert.deepEqual(secuencia(eventosDe(idMensaje)), ['sent', 'delivered', 'read', 'boton:VOY_EN_CAMINO']);
});

test('la respuesta del envío tiene el formato de Meta', async (t) => {
	const sim = await simulador(t);
	const respuesta = await enviar(sim, plantilla('+56 9 1111 1111'));
	assert.equal(respuesta.status, 200);
	const cuerpo = await respuesta.json();
	assert.equal(cuerpo.messaging_product, 'whatsapp');
	assert.equal(cuerpo.contacts[0].input, '+56 9 1111 1111');
	assert.equal(cuerpo.contacts[0].wa_id, '56911111111', 'wa_id son solo los dígitos');
	assert.match(cuerpo.messages[0].id, /^wamid\./);
});

test('cada envío recibe un id de mensaje distinto', async (t) => {
	const sim = await simulador(t);
	const a = await (await enviar(sim, plantilla('56911111111'))).json();
	const b = await (await enviar(sim, plantilla('56911111111'))).json();
	assert.notEqual(a.messages[0].id, b.messages[0].id);
});

test('el webhook llega a la ruta configurada, con el formato whatsapp_business_account', async (t) => {
	const { eventos } = await enviarYEsperar(t, '56911113331');
	const estado = eventos[0];
	const llegada = recibidos.find((r) => r.json.entry?.[0]?.changes?.[0]?.value === undefined
		? false
		: r.json.entry[0].changes[0].value.statuses?.[0]?.id === estado.elemento.id);
	assert.equal(llegada.metodo, 'POST');
	assert.equal(llegada.ruta, '/api/webhooks/whatsapp');
	assert.equal(llegada.json.object, 'whatsapp_business_account');
	assert.equal(llegada.json.entry[0].changes[0].field, 'messages');
	assert.equal(estado.valorCambio.messaging_product, 'whatsapp');
	assert.equal(estado.valorCambio.metadata.phone_number_id, ID_NUMERO, 'el id del número es el de la ruta del envío');
	assert.equal(estado.elemento.recipient_id, '56911113331');
	assert.match(estado.elemento.timestamp, /^\d+$/, 'timestamp en segundos, como texto');
});

test('el botón viene del número del contacto y apunta al mensaje original', async (t) => {
	const { idMensaje, eventos } = await enviarYEsperar(t, '56911114441');
	const boton = eventos.at(-1);
	assert.equal(boton.elemento.type, 'button');
	assert.equal(boton.elemento.from, '56911114441');
	assert.equal(boton.elemento.context.id, idMensaje);
	assert.match(boton.elemento.id, /^wamid\./);
	assert.notEqual(boton.elemento.id, idMensaje, 'la respuesta es otro mensaje');
	assert.equal(boton.valorCambio.contacts[0].wa_id, '56911114441');
});

test('sin payload en la plantilla, el payload del botón es su texto (como en Meta)', async (t) => {
	const sim = await simulador(t);
	const cuerpo = plantilla('56911115551');
	cuerpo.template.components = cuerpo.template.components.filter((c) => c.type !== 'button');
	const idMensaje = (await (await enviar(sim, cuerpo)).json()).messages[0].id;
	await esperar(MARGEN_MS);
	const boton = eventosDe(idMensaje).at(-1);
	assert.equal(boton.tipo, 'boton');
	assert.equal(boton.elemento.button.payload, 'Voy en camino');
});

test('mensaje interactivo con botones: responde con interactive.button_reply', async (t) => {
	const sim = await simulador(t);
	const respuesta = await enviar(sim, {
		messaging_product: 'whatsapp',
		to: '56911116662',
		type: 'interactive',
		interactive: {
			type: 'button',
			body: { text: 'Alerta' },
			action: {
				buttons: [
					{ type: 'reply', reply: { id: 'VOY_EN_CAMINO', title: 'Voy en camino' } },
					{ type: 'reply', reply: { id: 'NO_PUEDO', title: 'No puedo' } },
				],
			},
		},
	});
	const idMensaje = (await respuesta.json()).messages[0].id;
	await esperar(MARGEN_MS);
	const llegadas = recibidos.flatMap((r) => r.json.entry[0].changes[0].value.messages ?? [])
		.filter((m) => m.context?.id === idMensaje);
	assert.equal(llegadas.length, 1);
	assert.equal(llegadas[0].type, 'interactive');
	assert.deepEqual(llegadas[0].interactive, { type: 'button_reply', button_reply: { id: 'NO_PUEDO', title: 'No puedo' } });
});

test('cada webhook va firmado con X-Hub-Signature-256 = HMAC-SHA256 del cuerpo con el secreto', async (t) => {
	const { idMensaje } = await enviarYEsperar(t, '56911117771');
	const propios = recibidos.filter((r) => r.crudo.includes(idMensaje));
	assert.equal(propios.length, 4);
	for (const r of propios) {
		const esperada = `sha256=${createHmac('sha256', SECRETO).update(r.crudo).digest('hex')}`;
		assert.equal(r.cabeceras['x-hub-signature-256'], esperada);
		const conOtroSecreto = `sha256=${createHmac('sha256', 'otro').update(r.crudo).digest('hex')}`;
		assert.notEqual(r.cabeceras['x-hub-signature-256'], conOtroSecreto);
		assert.match(r.cabeceras['content-type'], /^application\/json/);
	}
});

test('sin token de acceso responde 401 y no avisa al webhook', async (t) => {
	const sim = await simulador(t);
	const antes = recibidos.length;
	const respuesta = await enviar(sim, plantilla('56911111111'), { Authorization: '' });
	assert.equal(respuesta.status, 401);
	assert.ok((await respuesta.json()).error, 'error con el formato de Meta');
	await esperar(MARGEN_MS);
	assert.equal(recibidos.length, antes);
});

test('con un token distinto del configurado responde 401', async (t) => {
	const sim = await simulador(t);
	const respuesta = await enviar(sim, plantilla('56911111111'), { Authorization: 'Bearer otro' });
	assert.equal(respuesta.status, 401);
	assert.equal((await respuesta.json()).error.code, 190);
});

test('sin token configurado acepta cualquier token', async (t) => {
	const sim = await simulador(t, { token: undefined });
	const respuesta = await enviar(sim, plantilla('56911111111'), { Authorization: 'Bearer cualquiera' });
	assert.equal(respuesta.status, 200);
});

test('envío inválido (sin destinatario, producto distinto o JSON roto) responde 400 sin avisar', async (t) => {
	const sim = await simulador(t);
	const antes = recibidos.length;
	const sinDestino = plantilla('56911111111');
	delete sinDestino.to;
	const otroProducto = { ...plantilla('56911111111'), messaging_product: 'sms' };
	for (const cuerpo of [sinDestino, otroProducto]) {
		const respuesta = await enviar(sim, cuerpo);
		assert.equal(respuesta.status, 400);
		assert.equal((await respuesta.json()).error.code, 100);
	}
	const roto = await fetch(`${sim.url}/v21.0/${ID_NUMERO}/messages`, {
		method: 'POST',
		headers: { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' },
		body: '{no es json',
	});
	assert.equal(roto.status, 400);
	await esperar(MARGEN_MS);
	assert.equal(recibidos.length, antes);
});

test('otra ruta u otro método responde 404', async (t) => {
	const sim = await simulador(t);
	assert.equal((await enviar(sim, plantilla('56911111111'), {}, '/v21.0/123/otra')).status, 404);
	assert.equal((await fetch(`${sim.url}/v21.0/${ID_NUMERO}/messages`)).status, 404);
});

test('si el webhook no contesta, el simulador sigue funcionando', async (t) => {
	const sim = await simulador(t, { webhook: 'http://localhost:1/api/webhooks/whatsapp' });
	const respuesta = await enviar(sim, plantilla('56911111111'));
	assert.equal(respuesta.status, 200);
	await esperar(MARGEN_MS);
	assert.equal((await enviar(sim, plantilla('56911111111'))).status, 200);
});
