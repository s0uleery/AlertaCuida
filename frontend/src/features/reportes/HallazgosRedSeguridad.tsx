import { useState } from 'react';
import { Link } from 'react-router';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearFechaHora, hoyEnChile } from '../../comun/fechas';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { Panel } from '../../comun/ui/Panel';
import { TABLA, Tabla } from '../../comun/ui/Tabla';
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
    <Panel titulo="Posibles eventos no alertados">
      <p className="mb-4 text-[13px] text-apagado">
        Lecturas más allá del límite grave de cada persona sin una alerta del reloj cerca (30 minutos antes o después).
        Nadie fue avisado por ellas: sirven para revisar si algún reloj no está alertando.
      </p>

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="block w-44">
          <span className={CLASE_ETIQUETA}>Desde</span>
          <input
            type="date"
            value={desde}
            max={hasta}
            onChange={(e) => e.target.value && setDesde(e.target.value)}
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block w-44">
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

      {error && <Aviso tono="error">{error}</Aviso>}
      {cargando && !datos && <Cargando />}
      {!cargando && !error && hallazgos.length === 0 && (
        <Vacio>No hay lecturas graves sin alerta del reloj en estas fechas.</Vacio>
      )}
      {hallazgos.length > 0 && (
        <Tabla columnas={['Medida', 'Usuario', 'Lectura', 'Valor', 'Límite grave']} etiqueta="Posibles no alertadas">
          {hallazgos.map((h, i) => (
            <tr key={`${h.paciente.id}-${h.variable}-${h.fechaHoraMedicion}-${i}`} className={TABLA.fila}>
              <td className={`${TABLA.td} cifra whitespace-nowrap`}>{formatearFechaHora(h.fechaHoraMedicion)}</td>
              <td className={TABLA.td}>
                <Link to={`/usuarios/${h.paciente.id}`} className="font-bold text-teal-oscuro hover:underline">
                  {[h.paciente.nombres, h.paciente.apellidos].filter(Boolean).join(' ') || 'Sin nombre'}
                </Link>
              </td>
              <td className={TABLA.td}>{VARIABLE[h.variable].nombre}</td>
              <td className={`${TABLA.td} cifra font-bold text-magenta`}>
                {numero(h.valor)} {VARIABLE[h.variable].unidad}
              </td>
              <td className={TABLA.td}>{limite(h)}</td>
            </tr>
          ))}
        </Tabla>
      )}
    </Panel>
  );
}
