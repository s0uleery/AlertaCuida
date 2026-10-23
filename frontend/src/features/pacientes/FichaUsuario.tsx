import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { CLASE_BOTON_SECUNDARIO, CLASE_ERROR, CLASE_TARJETA } from '../../comun/estilos';
import { formatearFecha, formatearFechaHora } from '../../comun/fechas';
import { formatearRun } from '../../comun/run';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { pacientesApi } from './pacientesApi';
import { RelojDelUsuario } from './RelojDelUsuario';
import type { DatosPaciente } from './tipos';

const SEXO: Record<string, string> = { FEMENINO: 'Femenino', MASCULINO: 'Masculino' };
const NIVEL: Record<string, string> = { MODERADA: 'Moderada', SEVERA: 'Severa' };

/** Filas de la tabla de datos personales: etiqueta y cómo mostrar el valor. */
function filas(datos: DatosPaciente, edad: number | null): [string, string][] {
  const fecha = (valor: string | null) => (valor ? formatearFecha(valor) : '—');
  return [
    ['RUN', formatearRun(datos.run)],
    ['Alias', datos.alias ?? '—'],
    ['Nacimiento', datos.fechaNacimiento ? `${fecha(datos.fechaNacimiento)} (${edad} años)` : '—'],
    ['Sexo', datos.sexo ? SEXO[datos.sexo] : 'No informado'],
    ['Dirección', datos.direccion ?? '—'],
    ['Dependencia', datos.nivelDependencia ? NIVEL[datos.nivelDependencia] : 'Por evaluar'],
    ['Ingreso', fecha(datos.fechaIngreso)],
    ['Consentimiento', datos.fechaConsentimiento ? fecha(datos.fechaConsentimiento) : 'Sin registrar'],
  ];
}

/** Ficha de un usuario del programa (/usuarios/:id): datos, su reloj e historial de relojes. */
export function FichaUsuario() {
  const { id = '' } = useParams();
  const { puede } = useSesion();
  const { datos: detalle, error, cargando, recargar } = useDatos(() => pacientesApi.detalle(id), id);
  const [errorBaja, setErrorBaja] = useState<string | null>(null);

  if (cargando && !detalle) return <p className="text-slate-500">Cargando…</p>;
  if (error || !detalle) return <p className={CLASE_ERROR}>{error ?? 'No se encontró el usuario.'}</p>;

  const { paciente, edad, historial } = detalle;

  async function darDeBaja() {
    if (!window.confirm('¿Dar de baja a este usuario? Se le retirará el reloj; su historial se conserva.')) return;
    try {
      await pacientesApi.darDeBaja(paciente.id);
      recargar();
    } catch (e) {
      setErrorBaja(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <Link to="/usuarios" className="text-sm text-sky-700 hover:underline">
            ← Usuarios
          </Link>
          <h1 className="text-xl font-semibold text-slate-900">
            {paciente.datos.nombres} {paciente.datos.apellidos}
          </h1>
          {paciente.fechaBaja && (
            <p className="text-sm text-slate-500">Dado de baja el {formatearFecha(paciente.fechaBaja)}</p>
          )}
        </div>
        <div className="flex gap-2">
          {puede('USUARIOS:EDITAR') && (
            <Link to={`/usuarios/${paciente.id}/editar`} className={CLASE_BOTON_SECUNDARIO}>
              Editar
            </Link>
          )}
          {puede('USUARIOS:ELIMINAR') && !paciente.fechaBaja && (
            <button onClick={darDeBaja} className={`${CLASE_BOTON_SECUNDARIO} text-red-700`}>
              Dar de baja
            </button>
          )}
        </div>
      </div>

      {errorBaja && <p className={CLASE_ERROR}>{errorBaja}</p>}

      <section className={CLASE_TARJETA}>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Datos personales</h2>
        <dl className="grid gap-x-6 gap-y-2 sm:grid-cols-2">
          {filas(paciente.datos, edad).map(([etiqueta, valor]) => (
            <div key={etiqueta} className="flex gap-2">
              <dt className="w-32 shrink-0 text-sm text-slate-500">{etiqueta}</dt>
              <dd className="text-slate-800">{valor}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className={`${CLASE_TARJETA} space-y-4`}>
        <h2 className="text-lg font-semibold text-slate-900">Reloj</h2>
        <RelojDelUsuario detalle={detalle} alCambiar={recargar} />
      </section>

      <section className={CLASE_TARJETA}>
        <h2 className="mb-3 text-lg font-semibold text-slate-900">Historial de relojes</h2>
        {historial.length === 0 ? (
          <p className="text-slate-500">Nunca ha tenido reloj.</p>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="text-slate-500">
              <tr>
                <th className="py-2 font-medium">Reloj</th>
                <th className="py-2 font-medium">Desde</th>
                <th className="py-2 font-medium">Hasta</th>
              </tr>
            </thead>
            <tbody>
              {historial.map((asignacion) => (
                <tr key={asignacion.idAsignacion} className="border-t border-slate-100">
                  <td className="py-2 font-mono">{asignacion.identificadorReloj}</td>
                  <td className="py-2">{formatearFechaHora(asignacion.inicio)}</td>
                  <td className="py-2">
                    {asignacion.fin ? formatearFechaHora(asignacion.fin) : <span className="text-emerald-700">En uso</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
