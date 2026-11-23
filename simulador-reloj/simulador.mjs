#!/usr/bin/env node
/**
 * Simulador de reloj (versión mínima: solo los avisos del servidor).
 *
 * Se hace pasar por uno o varios relojes. Como el reloj real, se empareja con código: pide un código, lo
 * muestra y espera a que alguien lo escriba en el dashboard; nadie escribe ni copia su token. Guarda el token en
 * simulador-reloj/.tokens/ (fuera de git) para no emparejarse de nuevo en cada arranque. Después registra un
 * token FCM inventado por cada reloj (PUT /token-fcm) y escucha los avisos que le entrega el backend con
 * FCM_CANAL=SIMULADOR. Por cada aviso: lo muestra, acusa recibo si corresponde (POST .../recepcion) y,
 * si es CONFIGURACION_NUEVA, descarga la configuración (GET /configuracion).
 *
 * Uso y opciones: ver README.md de esta carpeta o `node simulador-reloj/simulador.mjs --ayuda`.
 * Sin dependencias: solo Node 18 o superior (fetch y http vienen incluidos).
 */
import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

/** Donde cada reloj simulado guarda su token, como el reloj real lo guarda en su memoria. Está en .gitignore. */
const CARPETA_TOKENS = join(dirname(fileURLToPath(import.meta.url)), '.tokens');

const AYUDA = `Simulador de reloj: recibe los avisos del backend (FCM_CANAL=SIMULADOR).

Uso:
  node simulador-reloj/simulador.mjs [opciones]

La primera vez, cada reloj muestra un código de emparejamiento: escríbelo en el dashboard ("Entregar reloj",
"Reemplazar reloj" o "Emparejar de nuevo"). Después el reloj recuerda su token y no hay que emparejarlo otra vez,
salvo que lo desvinculen.

Opciones:
  --relojes <n>      cuántos relojes simular a la vez (por defecto 1)
  --nuevo            olvida los tokens guardados y empareja de nuevo, como un reloj restaurado de fábrica
  --backend <url>    dirección del backend (por defecto http://localhost:8080)
  --puerto <n>       puerto donde escucha los avisos (por defecto 9090, igual que FCM_SIMULADOR_URL)
  --sin-acuse        no acusa los avisos: sirve para ver los reintentos del servidor (1, 1 y 2 min)
  --rechazar-token   responde 410 a todo aviso: el servidor debe olvidar el token del reloj
  --ayuda            muestra esta ayuda`;

/**
 * Lee las opciones de la línea de comandos.
 * @returns {{relojes: number, nuevo: boolean, backend: string, puerto: number, sinAcuse: boolean,
 *   rechazarToken: boolean}}
 */
function leerOpciones() {
	const { values } = parseArgs({
		options: {
			relojes: { type: 'string', default: '1' },
			nuevo: { type: 'boolean', default: false },
			backend: { type: 'string', default: 'http://localhost:8080' },
			puerto: { type: 'string', default: '9090' },
			'sin-acuse': { type: 'boolean', default: false },
			'rechazar-token': { type: 'boolean', default: false },
			ayuda: { type: 'boolean', default: false },
		},
	});
	const relojes = Number(values.relojes);
	if (values.ayuda || !Number.isInteger(relojes) || relojes < 1) {
		console.log(AYUDA);
		process.exit(values.ayuda ? 0 : 1);
	}
	return {
		relojes,
		nuevo: values.nuevo,
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

/**
 * Empareja un reloj con código, como el reloj real al abrir la app sin token: pide el código, lo muestra
 * y pregunta cada pocos segundos si ya lo vincularon. Si el código vence, pide otro.
 * @param {object} opciones las de la línea de comandos
 * @param {string} nombre nombre corto del reloj simulado
 * @returns {Promise<string>} el token del reloj, que ya sirve para la API
 */
async function emparejar(opciones, nombre) {
	const url = `${opciones.backend}/api/reloj/v1/emparejamientos`;
	for (;;) {
		const pedido = await fetch(url, { method: 'POST' });
		if (pedido.status === 429) {
			const espera = Number(pedido.headers.get('Retry-After') ?? 60);
			anotar(nombre, `demasiados códigos pendientes: se reintenta en ${espera} s`);
			await new Promise((listo) => setTimeout(listo, espera * 1000));
			continue;
		}
		if (pedido.status !== 201) {
			throw new Error(`el backend no entregó un código: ${pedido.status} ${await pedido.text()}`);
		}
		const { codigo, token, expiraEn, segundosConsulta } = await pedido.json();
		const hasta = new Date(expiraEn).toLocaleTimeString('es-CL', { hour12: false });
		anotar(nombre, `CÓDIGO DE EMPAREJAMIENTO: ${codigo}  (escríbelo en el dashboard; vence a las ${hasta})`);
		for (;;) {
			await new Promise((listo) => setTimeout(listo, segundosConsulta * 1000));
			const consulta = await fetch(`${url}/consulta`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json' },
				body: JSON.stringify({ token }),
			});
			const { estado } = await consulta.json();
			if (estado === 'VINCULADO') {
				anotar(nombre, 'emparejado: el token ya sirve');
				return token;
			}
			if (estado === 'VENCIDO') {
				anotar(nombre, 'el código venció sin que lo escribieran: se pide otro');
				break;
			}
		}
	}
}

/**
 * Pregunta al backend en qué está el emparejamiento de un token.
 * @param {object} opciones las de la línea de comandos
 * @param {string} token el token del reloj
 * @returns {Promise<string>} PENDIENTE, VINCULADO o VENCIDO
 */
async function estadoDelToken(opciones, token) {
	const respuesta = await fetch(`${opciones.backend}/api/reloj/v1/emparejamientos/consulta`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ token }),
	});
	return (await respuesta.json()).estado;
}

/**
 * Entrega el token del reloj: el guardado si sigue vinculado; si no (o con --nuevo), empareja con código y lo
 * guarda. Así se comporta el reloj real al encenderse.
 * @param {object} opciones las de la línea de comandos
 * @param {string} nombre nombre corto del reloj simulado, que también nombra su archivo de token
 * @returns {Promise<string>} un token que sirve para la API
 */
async function tokenDelReloj(opciones, nombre) {
	const archivo = join(CARPETA_TOKENS, nombre);
	let guardado = null;
	try {
		guardado = opciones.nuevo ? null : readFileSync(archivo, 'utf8').trim();
	} catch {
		// Primera vez: no hay token guardado
	}
	if (guardado && (await estadoDelToken(opciones, guardado)) === 'VINCULADO') {
		anotar(nombre, 'sigue vinculado con su token guardado');
		return guardado;
	}
	if (guardado) {
		anotar(nombre, 'su token guardado ya no sirve (lo desvincularon o reemplazaron): se empareja de nuevo');
	}
	const token = await emparejar(opciones, nombre);
	mkdirSync(CARPETA_TOKENS, { recursive: true });
	writeFileSync(archivo, token, { mode: 0o600 });
	return token;
}

/** Punto de entrada. */
async function main() {
	const opciones = leerOpciones();
	const relojes = [];
	for (let i = 1; i <= opciones.relojes; i++) {
		const nombre = `reloj-${i}`;
		try {
			relojes.push({
				nombre,
				tokenApi: await tokenDelReloj(opciones, nombre),
				tokenFcm: `sim-${i}-${randomUUID()}`,
				recibidos: new Map(),
			});
		} catch (e) {
			console.error(`No se pudo emparejar ${nombre} con el backend en ${opciones.backend}: ${e.message}`);
			process.exit(1);
		}
	}
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
