/**
 * Reglas del reloj simulado que no dependen de la red: qué lecturas inventa, a quién avisa primero, si un
 * contacto está en su horario, si una alerta espera el día hábil y por qué canal pide el aviso.
 * Son funciones puras, para probarlas sin backend (decisiones del 19-11-2026).
 */

/** Zona horaria del programa: los horarios de los contactos y el calendario hábil van en hora de Chile. */
export const ZONA = 'America/Santiago';

/** Días de la semana como los nombra el calendario de la configuración (0 = domingo, como Date). */
const DIAS = ['DOMINGO', 'LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO'];

/** Rangos de las lecturas normales que inventa el simulador cuando no se pide una alerta. */
export const NORMAL = { FC: [60, 95], SPO2: [94, 99] };

/** Umbrales base, por si el reloj todavía no descargó su configuración. */
export const UMBRALES_BASE = {
	fc: { graveBajo: 40, normalMin: 50, normalMax: 110, graveAlto: 130 },
	spo2: { graveBajo: 88, normalMin: 92 },
};

/**
 * Una lectura normal al azar, dentro de {@link NORMAL} y además dentro del rango normal del paciente.
 * @param {'FC'|'SPO2'} tipo la variable
 * @param {object} umbrales los de la configuración (fc y spo2)
 * @param {Date} cuando la hora de medición
 * @param {() => number} aleatorio número entre 0 y 1
 * @returns {{tipo: string, valor: number, fechaHoraMedicion: string}} la lectura
 */
export function lecturaNormal(tipo, umbrales, cuando, aleatorio = Math.random) {
	const propio = tipo === 'FC' ? umbrales.fc : umbrales.spo2;
	const minimo = Math.max(NORMAL[tipo][0], propio.normalMin);
	const maximo = Math.min(NORMAL[tipo][1], propio.normalMax ?? 100);
	const valor = Math.round(minimo + aleatorio() * Math.max(0, maximo - minimo));
	return { tipo, valor, fechaHoraMedicion: cuando.toISOString() };
}

/**
 * Dos lecturas seguidas más allá del límite grave, separadas por el intervalo de medición: lo que exige la
 * regla del reloj para alertar por FC o SpO2. FC queda sobre el límite alto; SpO2, bajo el límite bajo.
 * @param {'FC'|'SPO2'} tipo la variable
 * @param {object} umbrales los de la configuración
 * @param {Date} hasta hora de la segunda lectura
 * @param {number} intervaloMs tiempo entre lecturas
 * @returns {object[]} las dos lecturas, de la más antigua a la más nueva
 */
export function lecturasGraves(tipo, umbrales, hasta, intervaloMs) {
	const valores = tipo === 'FC'
		? [umbrales.fc.graveAlto + 8, umbrales.fc.graveAlto + 12]
		: [umbrales.spo2.graveBajo - 3, umbrales.spo2.graveBajo - 4];
	return valores.map((valor, i) => ({
		tipo,
		valor,
		fechaHoraMedicion: new Date(hasta.getTime() - (1 - i) * intervaloMs).toISOString(),
	}));
}

/**
 * Lecturas de FC fuera del rango normal sin llegar al grave, durante los minutos de la regla de tendencia.
 * @param {object} umbrales los de la configuración
 * @param {Date} hasta hora de la última lectura
 * @param {number} minutos los de la regla de tendencia
 * @param {number} intervaloMs tiempo entre lecturas
 * @returns {object[]} de la más antigua a la más nueva
 */
export function lecturasTendencia(umbrales, hasta, minutos, intervaloMs) {
	const valor = Math.round((umbrales.fc.normalMax + umbrales.fc.graveAlto) / 2);
	const cantidad = Math.floor((minutos * 60_000) / intervaloMs) + 1;
	return Array.from({ length: cantidad }, (_, i) => ({
		tipo: 'FC',
		valor,
		fechaHoraMedicion: new Date(hasta.getTime() - (cantidad - 1 - i) * intervaloMs).toISOString(),
	}));
}

/**
 * Los contactos en el orden en que el reloj los avisa: solo los de la lista de la gravedad de la alerta, por su
 * lugar en ella (`prioridades` del contrato de la configuración). El botón de ayuda es leve: usa la lista de
 * leve. Si la alerta se agrava, se vuelve a llamar con GRAVE y se empieza por el primero.
 * @param {object[]} contactos los de la configuración
 * @param {string} severidad LEVE, MEDIA o GRAVE
 * @returns {object[]} una copia ordenada
 */
export function contactosEnOrden(contactos, severidad) {
	return contactos
		.filter((c) => c.prioridades?.[severidad] !== undefined)
		.sort((a, b) => a.prioridades[severidad] - b.prioridades[severidad]);
}

/**
 * Día de la semana, fecha y hora "HH:MM" de un instante en hora de Chile.
 * @param {Date} cuando el instante
 * @returns {{dia: string, fecha: string, hora: string}} sus partes en Chile
 */
export function enChile(cuando) {
	const partes = Object.fromEntries(new Intl.DateTimeFormat('en-CA', {
		timeZone: ZONA, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
		hourCycle: 'h23', weekday: 'short',
	}).formatToParts(cuando).map((p) => [p.type, p.value]));
	const semana = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(partes.weekday);
	return { dia: DIAS[semana], fecha: `${partes.year}-${partes.month}-${partes.day}`, hora: `${partes.hour}:${partes.minute}` };
}

/**
 * Si un contacto se puede avisar a esa hora. Sin horario, todo el día; si el inicio es posterior al fin, el
 * horario cruza la medianoche (por ejemplo 20:00 a 08:00).
 * @param {{inicio: string, fin: string}|null} horario el del contacto
 * @param {Date} cuando el instante
 * @returns {boolean} si está en su horario
 */
export function enHorario(horario, cuando) {
	if (!horario) {
		return true;
	}
	const { hora } = enChile(cuando);
	return horario.inicio <= horario.fin
		? hora >= horario.inicio && hora < horario.fin
		: hora >= horario.inicio || hora < horario.fin;
}

/**
 * Si una alerta queda esperando el día hábil: solo las leves fuera del horario hábil del calendario (o en
 * feriado). El reloj no avisa a nadie por ellas.
 * @param {string} severidad la de la alerta
 * @param {object|undefined} calendario el de la configuración
 * @param {Date} cuando hora de detección
 * @returns {boolean} si espera el día hábil
 */
export function esperaDiaHabil(severidad, calendario, cuando) {
	if (severidad !== 'LEVE' || !calendario) {
		return false;
	}
	const { dia, fecha, hora } = enChile(cuando);
	if ((calendario.feriados ?? []).includes(fecha)) {
		return true;
	}
	const tramo = (calendario.horarioHabil ?? []).find((h) => h.dia === dia);
	return !tramo || hora < tramo.inicio || hora >= tramo.fin;
}

/**
 * Canal por el que el reloj pide avisar a un contacto: WhatsApp si lo tiene; si no, el primero que tenga
 * (decisión del 19-11-2026: un aviso por contacto y ronda).
 * @param {{canales: string[]}} contacto el de la configuración
 * @returns {string} WHATSAPP o CORREO
 */
export function canalDe(contacto) {
	return contacto.canales.includes('WHATSAPP') ? 'WHATSAPP' : contacto.canales[0];
}
