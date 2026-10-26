import { Link } from 'react-router';
import { CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { formatearHoraReciente, hoyEnChile } from '../../comun/fechas';
import { useDatos } from '../../comun/useDatos';
import { alertasApi } from '../alertas/alertasApi';
import { CONDICION, ESTADO, InsigniaGravedad } from '../alertas/etiquetas';
import type { ResumenAlerta } from '../alertas/tipos';
import { useSesion } from '../sesion/SesionContext';
import { TarjetaResumen } from './TarjetaResumen';

/** true si la alerta llegó hoy, en la hora de Chile. */
function esDeHoy(alerta: ResumenAlerta): boolean {
  return new Date(alerta.recepcion).toLocaleDateString('sv-SE', { timeZone: 'America/Santiago' }) === hoyEnChile();
}

/** Portada: saludo, resumen de las alertas de hoy y las últimas alertas. */
export function PaginaInicio() {
  const { usuario, puede } = useSesion();
  if (!usuario) return null;

  const primerNombre = usuario.nombre.split(' ')[0];

  return (
    <div className="space-y-5">
      <section className="rounded-2xl bg-marino px-10 py-11 text-white">
        <h1 className="text-3xl font-bold">¡Hola {primerNombre}!</h1>
        <p className="mt-1.5 max-w-md">
          Te damos la bienvenida al monitoreo de alertas. Revisa y gestiona los casos de las personas mayores de
          manera más rápida y eficiente.
        </p>
      </section>

      {puede('ALERTAS:VER') && <ResumenDeAlertas />}
    </div>
  );
}

/** Las dos tarjetas de alertas de la portada. Solo para quien puede ver alertas. */
function ResumenDeAlertas() {
  const { datos, error, cargando } = useDatos(alertasApi.recientes);
  const alertas = datos ?? [];
  const deHoy = alertas.filter(esDeHoy);
  const contar = (...estados: ResumenAlerta['estadoAtencion'][]) =>
    deHoy.filter((a) => estados.includes(a.estadoAtencion)).length;

  return (
    <>
      <section className={CLASE_TARJETA}>
        <h1 className="text-2xl font-medium text-tinta">Alertas de hoy</h1>
        <p className="mb-4 text-tinta">Resumen de las alertas recibidas y su estado de atención.</p>
        {error && <p className={CLASE_ERROR}>{error}</p>}
        <div className="grid gap-5 md:grid-cols-2">
          <TarjetaResumen
            titulo="Casos gestionados"
            primera={{ etiqueta: 'En proceso', cantidad: contar('EN_ATENCION', 'EN_SEGUIMIENTO') }}
            segunda={{ etiqueta: 'Cerrados', cantidad: contar('RESUELTA') }}
            colores={{ fuerte: '#14540f', suave: '#6abf69' }}
          />
          <TarjetaResumen
            titulo="Alertas recibidas"
            primera={{ etiqueta: 'Nuevas', cantidad: contar('SIN_ASIGNAR') }}
            segunda={{ etiqueta: 'Revisadas', cantidad: contar('EN_ATENCION', 'EN_SEGUIMIENTO', 'RESUELTA') }}
            colores={{ fuerte: '#022b50', suave: '#b0b0b0' }}
          />
        </div>
      </section>

      <section className={CLASE_TARJETA}>
        <h1 className="text-2xl font-medium text-tinta">Últimas alertas</h1>
        <p className="mb-4 text-tinta">Selecciona una alerta para abrir la ficha del caso.</p>
        <div className="overflow-x-auto rounded-lg border border-prlac">
          <table className="w-full min-w-[720px] border-collapse text-left text-sm">
            <thead className="bg-prlac text-white">
              <tr>
                <th className="px-3 py-2.5 font-bold">Hora</th>
                <th className="px-3 py-2.5 font-bold">Usuario</th>
                <th className="px-3 py-2.5 font-bold">Gravedad</th>
                <th className="px-3 py-2.5 font-bold">Condición médica</th>
                <th className="px-3 py-2.5 font-bold">Estado</th>
                <th className="px-3 py-2.5 font-bold">Acción</th>
              </tr>
            </thead>
            <tbody>
              {alertas.map((alerta) => (
                <tr key={alerta.id} className="border-t border-slate-300 odd:bg-slate-50">
                  <td className="px-3 py-2.5">{formatearHoraReciente(alerta.recepcion)}</td>
                  <td className="px-3 py-2.5">
                    {alerta.paciente.nombres} {alerta.paciente.apellidos}
                    {alerta.paciente.edad !== null && `, ${alerta.paciente.edad}`}
                  </td>
                  <td className="px-3 py-2.5">
                    <InsigniaGravedad severidad={alerta.severidad} />
                  </td>
                  <td className="px-3 py-2.5">{CONDICION[alerta.tipoEvento]}</td>
                  <td className="px-3 py-2.5">{ESTADO[alerta.estadoAtencion]}</td>
                  <td className="px-3 py-2.5">
                    <Link
                      to={`/alertas/${alerta.id}`}
                      className="inline-block rounded-sm bg-prlac px-3 py-1.5 text-[13px] font-medium text-white hover:bg-prlac-oscuro"
                    >
                      Ver ficha
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {cargando && <p className="px-3 py-2.5 text-slate-500">Cargando…</p>}
          {!cargando && !error && alertas.length === 0 && (
            <p className="px-3 py-2.5 text-slate-500">Todavía no hay alertas.</p>
          )}
        </div>
      </section>
    </>
  );
}
