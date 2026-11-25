import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { hoyEnChile } from '../../comun/fechas';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { claseBoton } from '../../comun/ui/Boton';
import { BarraLeyenda, TiraMetricas } from '../../comun/ui/Metricas';
import { EncabezadoVista, Panel } from '../../comun/ui/Panel';
import { Pestanas } from '../../comun/ui/Pestanas';
import { Paginacion, TABLA, Tabla, usePaginacion } from '../../comun/ui/Tabla';
import { useDatos } from '../../comun/useDatos';
import { alertasApi } from '../alertas/alertasApi';
import { GRAVEDAD } from '../alertas/etiquetas';
import type { ResumenAlerta, Severidad } from '../alertas/tipos';
import { coincide } from '../pacientes/busqueda';
import { pacientesApi } from '../pacientes/pacientesApi';
import { useSesion } from '../sesion/SesionContext';
import { BotonesExportar } from './BotonesExportar';
import { HallazgosRedSeguridad } from './HallazgosRedSeguridad';
import { PERIODOS } from './periodos';
import { reportesApi } from './reportesApi';

type PestanaReportes = 'usuarios' | 'gestion' | 'redSeguridad';

/**
 * Reportería del mockup: seguimiento por usuario (diario y semanal), gestión operativa del equipo y los posibles
 * eventos no alertados de la red de seguridad.
 */
export function ListaReportes() {
  const { puede } = useSesion();
  const [pestana, setPestana] = useState<PestanaReportes>('usuarios');

  return (
    <>
      <EncabezadoVista
        titulo="Reportería"
        subtitulo="Evolución de cada persona usuaria y cómo el equipo ha gestionado las alertas."
      />
      <Pestanas
        etiqueta="Secciones de reportería"
        activa={pestana}
        alCambiar={setPestana}
        pestanas={[
          { clave: 'usuarios', texto: 'Seguimiento por usuario' },
          ...(puede('ALERTAS:VER') ? [{ clave: 'gestion' as const, texto: 'Gestión operativa' }] : []),
          { clave: 'redSeguridad', texto: 'Posibles no alertadas' },
        ]}
      />
      {pestana === 'usuarios' && <ReportesPorUsuario />}
      {pestana === 'gestion' && <GestionOperativa />}
      {pestana === 'redSeguridad' && <HallazgosRedSeguridad />}
    </>
  );
}

/**
 * Usuarios del programa con acceso a su reporte diario y semanal. Usa la misma lista y la misma búsqueda que la
 * sección Usuarios: quien se registra allí aparece aquí.
 */
function ReportesPorUsuario() {
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
    <Panel titulo="Seguimiento por usuario">
      <div className="mb-4 flex flex-wrap items-center gap-4">
        <input
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, alias o RUN"
          aria-label="Buscar"
          className={`${CLASE_CAMPO} mt-0 max-w-sm`}
        />
        <label className="flex items-center gap-2 text-[13px] text-apagado">
          <input type="checkbox" checked={incluirBajas} onChange={(e) => setIncluirBajas(e.target.checked)} />
          Mostrar dados de baja
        </label>
      </div>

      {error && <Aviso tono="error">{error}</Aviso>}
      {cargando && !datos && <Cargando />}
      {datos && visibles.length === 0 && (
        <Vacio>{busqueda ? 'Nadie coincide con la búsqueda.' : 'Todavía no hay usuarios registrados.'}</Vacio>
      )}
      {visibles.length > 0 && (
        <>
          <Tabla columnas={['Persona usuaria', 'RUN', 'Edad', 'Ver reporte']} etiqueta="Reportes por usuario">
            {paginacion.visibles.map(({ paciente, edad }) => (
              <tr
                key={paciente.id}
                className={TABLA.filaClic}
                onClick={() => navegar(`/reportes/${paciente.id}/semanal`)}
              >
                <td className={TABLA.td}>
                  <b className="text-navy">
                    {paciente.datos.apellidos}, {paciente.datos.nombres}
                  </b>
                  {paciente.fechaBaja && (
                    <span className="ml-2 rounded-[3px] bg-superficie-sub px-1.5 py-px text-[10.5px] font-bold text-apagado">
                      DE BAJA
                    </span>
                  )}
                </td>
                <td className={`${TABLA.td} cifra whitespace-nowrap`}>{paciente.datos.run}</td>
                <td className={`${TABLA.td} cifra`}>{edad ?? '—'}</td>
                <td className={TABLA.td}>
                  <div className="flex gap-2">
                    {PERIODOS.map(({ valor, etiqueta }) => (
                      <Link
                        key={valor}
                        to={`/reportes/${paciente.id}/${valor}`}
                        onClick={(e) => e.stopPropagation()}
                        className={claseBoton('contorno', true)}
                      >
                        {etiqueta}
                      </Link>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </Tabla>
          <Paginacion estado={paginacion} />
        </>
      )}
    </Panel>
  );
}

/** @returns la fecha "AAAA-MM-DD" n días antes */
function diasAntes(fecha: string, dias: number): string {
  const d = new Date(`${fecha}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() - dias);
  return d.toISOString().slice(0, 10);
}

/** @returns el día de Chile en que llegó la alerta */
function diaDeRecepcion(alerta: ResumenAlerta): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Santiago' }).format(new Date(alerta.recepcion));
}

/**
 * Gestión operativa del mockup, calculada con la lista de alertas (sin simulacros): totales por estado,
 * distribución por gravedad y carga por profesional en el período elegido, con exportación.
 */
function GestionOperativa() {
  const hoy = hoyEnChile();
  const [desde, setDesde] = useState(diasAntes(hoy, 29));
  const [hasta, setHasta] = useState(hoy);
  const { datos, error, cargando } = useDatos(alertasApi.listar);
  const alRevés = hasta < desde;

  const delPeriodo = useMemo(
    () =>
      (datos ?? []).filter((a) => {
        if (a.simulacro) return false;
        const dia = diaDeRecepcion(a);
        return dia >= desde && dia <= hasta;
      }),
    [datos, desde, hasta],
  );
  const contar = (filtro: (a: ResumenAlerta) => boolean) => delPeriodo.filter(filtro).length;
  const total = delPeriodo.length;
  const cerradas = contar((a) => a.estadoAtencion === 'RESUELTA');
  const carga = useMemo(() => {
    const porPersona = new Map<string, { abiertas: number; cerradas: number }>();
    for (const a of delPeriodo) {
      const nombre = a.responsable ?? 'Por asignar';
      const fila = porPersona.get(nombre) ?? { abiertas: 0, cerradas: 0 };
      if (a.estadoAtencion === 'RESUELTA') fila.cerradas++;
      else fila.abiertas++;
      porPersona.set(nombre, fila);
    }
    return [...porPersona.entries()].sort((a, b) => b[1].abiertas + b[1].cerradas - (a[1].abiertas + a[1].cerradas));
  }, [delPeriodo]);

  return (
    <>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <label className="block w-44">
            <span className={CLASE_ETIQUETA}>Desde</span>
            <input type="date" value={desde} max={hoy} onChange={(e) => setDesde(e.target.value)} className={CLASE_CAMPO} />
          </label>
          <label className="block w-44">
            <span className={CLASE_ETIQUETA}>Hasta</span>
            <input type="date" value={hasta} max={hoy} onChange={(e) => setHasta(e.target.value)} className={CLASE_CAMPO} />
          </label>
        </div>
        {!alRevés && <BotonesExportar exportar={(formato) => reportesApi.exportarGestion(desde, hasta, formato)} />}
      </div>

      {alRevés && <Aviso tono="advertencia">El período termina antes de empezar.</Aviso>}
      {error && <Aviso tono="error">{error}</Aviso>}
      {cargando && !datos && <Cargando />}
      {datos && !alRevés && (
        <>
          <TiraMetricas
            metricas={[
              { titulo: 'Alertas del período', valor: total, nota: 'Sin contar simulacros' },
              {
                titulo: 'Sin asignar',
                valor: contar((a) => a.estadoAtencion === 'SIN_ASIGNAR'),
                destacada: contar((a) => a.estadoAtencion === 'SIN_ASIGNAR') > 0,
                nota: 'Nadie ha tomado el caso',
              },
              {
                titulo: 'En curso',
                valor: contar((a) => a.estadoAtencion === 'EN_ATENCION' || a.estadoAtencion === 'EN_SEGUIMIENTO'),
                nota: 'En atención o en seguimiento',
              },
              {
                titulo: 'Cerradas',
                valor: cerradas,
                nota: total > 0 ? `${Math.round((cerradas / total) * 100)} % del período` : '—',
              },
            ]}
          />
          <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
            <Panel titulo="Distribución por gravedad">
              <div className="grid gap-2">
                {(['GRAVE', 'MEDIA', 'LEVE'] as Severidad[]).map((g) => (
                  <BarraLeyenda
                    key={g}
                    tono={g === 'GRAVE' ? 'magenta' : g === 'MEDIA' ? 'naranja' : 'verde'}
                    valor={contar((a) => a.severidad === g)}
                    texto={GRAVEDAD[g].texto.toLowerCase()}
                    total={total}
                  />
                ))}
              </div>
            </Panel>
            <Panel titulo="Carga por profesional">
              {carga.length === 0 ? (
                <Vacio>Sin alertas en el período.</Vacio>
              ) : (
                <Tabla columnas={['Profesional', 'Abiertas', 'Cerradas', 'Total']} etiqueta="Carga por profesional">
                  {carga.map(([nombre, fila]) => (
                    <tr key={nombre} className={TABLA.fila}>
                      <td className={`${TABLA.td} ${nombre === 'Por asignar' ? 'font-bold text-magenta' : 'text-navy'}`}>
                        {nombre}
                      </td>
                      <td className={`${TABLA.td} cifra`}>{fila.abiertas}</td>
                      <td className={`${TABLA.td} cifra`}>{fila.cerradas}</td>
                      <td className={`${TABLA.td} cifra font-bold`}>{fila.abiertas + fila.cerradas}</td>
                    </tr>
                  ))}
                </Tabla>
              )}
            </Panel>
          </div>
          <p className="mt-3 text-[11.5px] text-apagado">
            Se calcula con las alertas más recientes que entrega el servidor. La exportación lleva además el detalle
            de cada alerta del período.
          </p>
        </>
      )}
    </>
  );
}
