import { useState } from 'react';
import { Link } from 'react-router';
import { CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearFechaHora, hoyEnChile } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { hallazgosApi, type Hallazgo } from './hallazgosApi';

/** Nombre y unidad de cada variable. */
const VARIABLE: Record<Hallazgo['variable'], { nombre: string; unidad: string }> = {
  FC: { nombre: 'Frecuencia cardíaca', unidad: 'lpm' },
  SPO2: { nombre: 'Saturación de oxígeno', unidad: '%' },
};

/** @returns el día AAAA-MM-DD que está tantos días antes del indicado */
function diasAntes(dia: string, dias: number): string {
  const fecha = new Date(`${dia}T12:00:00Z`);
  fecha.setUTCDate(fecha.getUTCDate() - dias);
  return fecha.toISOString().slice(0, 10);
}

/** @returns el número sin decimales innecesarios: 135 o 88,5 */
function numero(valor: number): string {
  return valor.toLocaleString('es-CL', { maximumFractionDigits: 1 });
}

/** @returns "sobre 130 lpm" o "bajo 88 %" */
function limite(hallazgo: Hallazgo): string {
  const { unidad } = VARIABLE[hallazgo.variable];
  const lado = hallazgo.valor > hallazgo.limiteGrave ? 'sobre' : 'bajo';
  return `${lado} ${numero(hallazgo.limiteGrave)} ${unidad}`;
}

/**
 * Red de seguridad: lecturas más allá del límite grave para las que el reloj no mandó una alerta a 30 minutos
 * o menos. Las alertas las decide el reloj; esta lista no avisa a nadie, sirve para revisar si el reloj está
 * fallando. Por defecto muestra los últimos 7 días, y como máximo 31.
 */
export function HallazgosRedSeguridad() {
  const hoy = hoyEnChile();
  const [desde, setDesde] = useState(diasAntes(hoy, 6));
  const [hasta, setHasta] = useState(hoy);
  const { datos, error, cargando } = useDatos(() => hallazgosApi.listar(desde, hasta), `${desde}|${hasta}`);
  const hallazgos = datos ?? [];

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-500">
        Lecturas más allá del límite grave de cada persona sin una alerta del reloj cerca (30 minutos antes o después).
        Nadie fue avisado por ellas: sirven para revisar si algún reloj no está alertando.
      </p>

      <div className="flex flex-wrap items-end gap-3">
        <label className="block">
          <span className={CLASE_ETIQUETA}>Desde</span>
          <input
            type="date"
            value={desde}
            max={hasta}
            onChange={(e) => e.target.value && setDesde(e.target.value)}
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block">
          <span className={CLASE_ETIQUETA}>Hasta</span>
          <input
            type="date"
            value={hasta}
            min={desde}
            max={hoy}
            onChange={(e) => e.target.value && setHasta(e.target.value)}
            className={CLASE_CAMPO}
          />
        </label>
      </div>

      {error && <p className={CLASE_ERROR}>{error}</p>}

      <div className="overflow-x-auto rounded-lg border border-prlac">
        <table className="w-full min-w-[640px] border-collapse text-left text-sm">
          <thead className="bg-prlac text-white">
            <tr>
              <th className="px-3 py-2.5 font-bold">Medida</th>
              <th className="px-3 py-2.5 font-bold">Usuario</th>
              <th className="px-3 py-2.5 font-bold">Lectura</th>
              <th className="px-3 py-2.5 font-bold">Valor</th>
              <th className="px-3 py-2.5 font-bold">Límite grave</th>
            </tr>
          </thead>
          <tbody>
            {hallazgos.map((h, i) => (
              <tr
                key={`${h.paciente.id}-${h.variable}-${h.fechaHoraMedicion}-${i}`}
                className="border-t border-slate-300"
              >
                <td className="px-3 py-2.5">{formatearFechaHora(h.fechaHoraMedicion)}</td>
                <td className="px-3 py-2.5">
                  <Link to={`/usuarios/${h.paciente.id}`} className="text-sky-700 hover:underline">
                    {[h.paciente.nombres, h.paciente.apellidos].filter(Boolean).join(' ') || 'Sin nombre'}
                  </Link>
                </td>
                <td className="px-3 py-2.5">{VARIABLE[h.variable].nombre}</td>
                <td className="px-3 py-2.5 font-medium tabular-nums text-[#a1111a]">
                  {numero(h.valor)} {VARIABLE[h.variable].unidad}
                </td>
                <td className="px-3 py-2.5">{limite(h)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {cargando && <p className="px-3 py-2.5 text-slate-500">Cargando…</p>}
        {!cargando && !error && hallazgos.length === 0 && (
          <p className="px-3 py-2.5 text-slate-500">No hay lecturas graves sin alerta del reloj en estas fechas.</p>
        )}
      </div>
    </div>
  );
}
