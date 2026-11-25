import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { claseBoton } from '../../comun/ui/Boton';
import { Insignia } from '../../comun/ui/Insignia';
import { EncabezadoVista } from '../../comun/ui/Panel';
import { Paginacion, TABLA, Tabla, usePaginacion } from '../../comun/ui/Tabla';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { coincide } from './busqueda';
import { pacientesApi } from './pacientesApi';

/** Cómo se muestra cada nivel de dependencia. */
const NIVEL: Record<string, string> = { MODERADA: 'Moderada', SEVERA: 'Severa' };

/**
 * Padrón de usuarios del programa, con el diseño del mockup: búsqueda, vigentes o con bajas, y paginación. Se
 * filtra aquí y no en el backend: los nombres van cifrados en la base y el piloto tiene pocos usuarios. Cada fila
 * abre la ficha del usuario.
 */
export function ListaUsuarios() {
  const { puede } = useSesion();
  const navegar = useNavigate();
  const [incluirBajas, setIncluirBajas] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const { datos, error, cargando } = useDatos(() => pacientesApi.listar(incluirBajas), String(incluirBajas));

  const visibles = useMemo(
    () => (datos ?? []).filter((fila) => !busqueda.trim() || coincide(fila, busqueda.trim())),
    [datos, busqueda],
  );
  const paginacion = usePaginacion(visibles, 10);

  return (
    <>
      <EncabezadoVista
        titulo="Padrón de usuarios"
        subtitulo="Personas mayores incorporadas al programa Red Local de Apoyos y Cuidados en Coquimbo."
        acciones={
          puede('USUARIOS:CREAR') && (
            <Link to="/usuarios/nuevo" className={claseBoton('principal')}>
              + Registrar usuario
            </Link>
          )
        }
      />

      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="block min-w-56 flex-1">
          <span className={CLASE_ETIQUETA}>Buscar en el padrón</span>
          <input
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Nombre, alias o RUN"
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block w-52">
          <span className={CLASE_ETIQUETA}>Estado</span>
          <select
            value={String(incluirBajas)}
            onChange={(e) => setIncluirBajas(e.target.value === 'true')}
            className={CLASE_CAMPO}
          >
            <option value="false">Vigentes</option>
            <option value="true">Incluir dados de baja</option>
          </select>
        </label>
      </div>

      {error && <Aviso tono="error" className="mb-4">{error}</Aviso>}

      {cargando && !datos ? (
        <Cargando />
      ) : visibles.length === 0 ? (
        <Vacio>{busqueda ? 'Nadie coincide con la búsqueda.' : 'Todavía no hay usuarios registrados.'}</Vacio>
      ) : (
        <>
          <Tabla columnas={['Persona usuaria', 'RUN', 'Edad', 'Dependencia', 'Reloj vinculado', null]} etiqueta="Padrón de usuarios">
            {paginacion.visibles.map(({ paciente, edad, relojActual }) => (
              <tr
                key={paciente.id}
                tabIndex={0}
                className={TABLA.filaClic}
                onClick={() => navegar(`/usuarios/${paciente.id}`)}
                onKeyDown={(e) => e.key === 'Enter' && navegar(`/usuarios/${paciente.id}`)}
              >
                <td className={TABLA.td}>
                  <b className="text-navy">
                    {paciente.datos.nombres} {paciente.datos.apellidos}
                  </b>
                  {paciente.datos.alias && <span className="text-apagado"> · “{paciente.datos.alias}”</span>}
                  <div className="mt-1 flex flex-wrap gap-1">
                    {paciente.fechaBaja && <Insignia tono="neutra">De baja</Insignia>}
                    {!paciente.datos.fechaConsentimiento && !paciente.fechaBaja && (
                      <Insignia tono="media">Sin consentimiento</Insignia>
                    )}
                  </div>
                </td>
                <td className={`${TABLA.td} cifra whitespace-nowrap`}>{paciente.datos.run}</td>
                <td className={`${TABLA.td} cifra`}>{edad ?? '—'}</td>
                <td className={TABLA.td}>
                  {paciente.datos.nivelDependencia ? NIVEL[paciente.datos.nivelDependencia] : 'Por evaluar'}
                </td>
                <td className={TABLA.td}>
                  {relojActual ? (
                    <span className="cifra rounded border border-teal-borde bg-teal-tinte px-1.5 py-0.5 text-[12px] text-navy">
                      {relojActual}
                    </span>
                  ) : (
                    <span className="text-apagado">Sin reloj</span>
                  )}
                </td>
                <td className={`${TABLA.td} text-right`}>
                  <span className={claseBoton('contorno', true)}>Ver ficha</span>
                </td>
              </tr>
            ))}
          </Tabla>
          <Paginacion estado={paginacion} />
        </>
      )}
    </>
  );
}
