#!/usr/bin/env node
/**
 * Simulador de reloj (versión mínima: solo los avisos del servidor).
 *
 * Se hace pasar por uno o varios relojes ya vinculados a un paciente. Al partir, registra un token FCM
 * inventado por cada reloj (PUT /token-fcm) y escucha los avisos que le entrega el backend con
 * FCM_CANAL=SIMULADOR. Por cada aviso: lo muestra, acusa recibo si corresponde (POST .../recepcion) y,
 * si es CONFIGURACION_NUEVA, descarga la configuración (GET /configuracion).
 *
 * Uso y opciones: ver README.md de esta carpeta o `node simulador-reloj/simulador.mjs --ayuda`.
 * Sin dependencias: solo Node 18 o superior (fetch y http vienen incluidos).
 */
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { parseArgs } from 'node:util';

const AYUDA = `Simulador de reloj: recibe los avisos del backend (FCM_CANAL=SIMULADOR).

Uso:
  node simulador-reloj/simulador.mjs --token <token del reloj> [--token <otro>] [opciones]

Opciones:
  --token <t>        token del reloj que mostró el dashboard al vincularlo (se puede repetir)
  --backend <url>    dirección del backend (por defecto http://localhost:8080)
  --puerto <n>       puerto donde escucha los avisos (por defecto 9090, igual que FCM_SIMULADOR_URL)
  --sin-acuse        no acusa los avisos: sirve para ver los reintentos del servidor (1, 1 y 2 min)
  --rechazar-token   responde 410 a todo aviso: el servidor debe olvidar el token del reloj
  --ayuda            muestra esta ayuda`;

/**
 * Lee las opciones de la línea de comandos.
 * @returns {{tokens: string[], backend: string, puerto: number, sinAcuse: boolean, rechazarToken: boolean}}
 */
function leerOpciones() {
	const { values } = parseArgs({
		options: {
			token: { type: 'string', multiple: true, default: [] },
			backend: { type: 'string', default: 'http://localhost:8080' },
			puerto: { type: 'string', default: '9090' },
			'sin-acuse': { type: 'boolean', default: false },
			'rechazar-token': { type: 'boolean', default: false },
			ayuda: { type: 'boolean', default: false },
		},
	});
	if (values.ayuda || values.token.length === 0) {
		console.log(AYUDA);
		process.exit(values.ayuda ? 0 : 1);
	}
	return {
		tokens: values.token,
		backend: values.backend.replace(/\/$/, ''),
		puerto: Number(values.puerto),
		sinAcuse: values['sin-acuse'],
		rechazarToken: values['rechazar-token'],
	};
}

/**
 * Escribe una línea con la hora local, para seguir los plazos de los reintentos.
 * @param {string} reloj nombre corto del reloj simulado
 * @param {string} texto lo que pasó
 */
function anotar(reloj, texto) {
	const hora = new Date().toLocaleTimeString('es-CL', { hour12: false });
	console.log(`${hora}  [${reloj}] ${texto}`);
}

/**
 * Llama a la API del reloj con su token.
 * @param {object} opciones las de la línea de comandos
 * @param {object} reloj el reloj simulado que llama
 * @param {string} metodo GET, PUT o POST
 * @param {string} ruta ruta bajo /api/reloj/v1
 * @param {object} [cuerpo] JSON a enviar
 * @returns {Promise<Response>} la respuesta del backend
 */
function llamar(opciones, reloj, metodo, ruta, cuerpo) {
	return fetch(`${opciones.backend}/api/reloj/v1${ruta}`, {
		method: metodo,
		headers: {
			Authorization: `Bearer ${reloj.tokenApi}`,
			...(cuerpo ? { 'Content-Type': 'application/json' } : {}),
		},
		body: cuerpo ? JSON.stringify(cuerpo) : undefined,
	});
}

/**
 * Registra el token FCM inventado del reloj, como hace el reloj real al instalarse.
 * @param {object} opciones las de la línea de comandos
 * @param {object} reloj el reloj simulado
 * @returns {Promise<boolean>} si el backend lo aceptó
 */
async function registrarTokenFcm(opciones, reloj) {
	const respuesta = await llamar(opciones, reloj, 'PUT', '/token-fcm', { token: reloj.tokenFcm });
	anotar(reloj.nombre, respuesta.status === 204
		? `token FCM registrado (${reloj.tokenFcm})`
		: `el backend no aceptó el token FCM: ${respuesta.status} ${await respuesta.text()}`);
	return respuesta.status === 204;
}

/**
 * Atiende un aviso: lo muestra, lo acusa si corresponde y descarga la configuración si cambió.
 * @param {object} opciones las de la línea de comandos
 * @param {object} reloj el reloj al que va el aviso
 * @param {Record<string, string>} datos los datos del aviso (esquema AvisoFcm)
 */
async function atender(opciones, reloj, datos) {
	const { tipo, idAviso, eventIdAlerta, idContacto } = datos;
	const veces = idAviso ? (reloj.recibidos.get(idAviso) ?? 0) + 1 : 1;
	if (idAviso) {
		reloj.recibidos.set(idAviso, veces);
	}
	const detalle = [
		eventIdAlerta && `alerta ${eventIdAlerta}`,
		idContacto && `contacto ${idContacto}`,
		idAviso && `aviso ${idAviso}`,
		veces > 1 && `REPETIDO (vez ${veces})`,
	].filter(Boolean).join(', ');
	anotar(reloj.nombre, `llegó ${tipo}${detalle ? `: ${detalle}` : ''}`);

	if (tipo === 'CONFIGURACION_NUEVA') {
		const respuesta = await llamar(opciones, reloj, 'GET', '/configuracion');
		const config = respuesta.ok ? await respuesta.json() : null;
		anotar(reloj.nombre, config
			? `configuración descargada: ${config.contactos?.length ?? 0} contactos`
			: `no se pudo descargar la configuración: ${respuesta.status}`);
		return;
	}
	if (!idAviso) {
		return;
	}
	if (opciones.sinAcuse) {
		anotar(reloj.nombre, 'no se acusa (--sin-acuse): el servidor debería reintentar');
		return;
	}
	const respuesta = await llamar(opciones, reloj, 'POST', `/avisos-fcm/${idAviso}/recepcion`,
		{ fechaHoraRecepcion: new Date().toISOString() });
	anotar(reloj.nombre, respuesta.status === 204
		? 'acuse enviado'
		: `el backend no aceptó el acuse: ${respuesta.status} ${await respuesta.text()}`);
}

/**
 * Lee el cuerpo JSON de un pedido HTTP.
 * @param {import('node:http').IncomingMessage} pedido el pedido del backend
 * @returns {Promise<any>} el JSON recibido
 */
async function leerJson(pedido) {
	const partes = [];
	for await (const parte of pedido) {
		partes.push(parte);
	}
	return JSON.parse(Buffer.concat(partes).toString('utf8'));
}

/**
 * Levanta el servidor que recibe los avisos del backend. Responde de inmediato (como Firebase, que
 * acepta el mensaje y lo entrega después) y atiende el aviso aparte.
 * @param {object} opciones las de la línea de comandos
 * @param {Map<string, object>} porTokenFcm los relojes simulados, por su token FCM
 */
function escucharAvisos(opciones, porTokenFcm) {
	const servidor = createServer(async (pedido, respuesta) => {
		if (pedido.method !== 'POST' || pedido.url !== '/avisos') {
			respuesta.writeHead(404).end();
			return;
		}
		let aviso;
		try {
			aviso = await leerJson(pedido);
		} catch {
			respuesta.writeHead(400).end();
			return;
		}
		const reloj = porTokenFcm.get(aviso.token);
		if (!reloj || opciones.rechazarToken) {
			anotar(reloj?.nombre ?? '?', `aviso ${aviso.datos?.tipo} rechazado con 410 (token desconocido)`);
			respuesta.writeHead(410).end();
			return;
		}
		respuesta.writeHead(204).end();
		atender(opciones, reloj, aviso.datos).catch((e) => anotar(reloj.nombre, `error: ${e.message}`));
	});
	servidor.listen(opciones.puerto, () => {
		console.log(`Escuchando avisos en http://localhost:${opciones.puerto}/avisos (Ctrl+C para salir)`);
	});
}

/** Punto de entrada. */
async function main() {
	const opciones = leerOpciones();
	const relojes = opciones.tokens.map((tokenApi, i) => ({
		nombre: `reloj-${i + 1}`,
		tokenApi,
		tokenFcm: `sim-${i + 1}-${randomUUID()}`,
		recibidos: new Map(),
	}));
	escucharAvisos(opciones, new Map(relojes.map((r) => [r.tokenFcm, r])));
	for (const reloj of relojes) {
		try {
			await registrarTokenFcm(opciones, reloj);
		} catch (e) {
			anotar(reloj.nombre, `no se pudo conectar con el backend en ${opciones.backend}: ${e.message}`);
		}
	}
}

main();
