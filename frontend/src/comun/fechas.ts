// Fechas tal como las ve el personal: en la zona horaria de Chile. El backend guarda todo en UTC.

const ZONA = 'America/Santiago';

/** Fecha y hora, por ejemplo "24-10-2026, 09:30". Recibe un instante ISO en UTC. */
export function formatearFechaHora(iso: string): string {
  return new Date(iso).toLocaleString('es-CL', { timeZone: ZONA, dateStyle: 'short', timeStyle: 'short' });
}

/** Solo la fecha, por ejemplo "24-10-2026". Recibe una fecha sin hora ("2026-10-24"). */
export function formatearFecha(fecha: string): string {
  const [anio, mes, dia] = fecha.split('-');
  return `${dia}-${mes}-${anio}`;
}

/** Hoy en Chile como "2026-10-24", el formato de los campos de tipo fecha y del backend. */
export function hoyEnChile(): string {
  // El formato sueco (sv-SE) es justo AAAA-MM-DD
  return new Date().toLocaleDateString('sv-SE', { timeZone: ZONA });
}
