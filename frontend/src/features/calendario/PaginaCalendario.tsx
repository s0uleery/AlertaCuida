import { useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearFecha, hoyEnChile } from '../../comun/fechas';
import { Aviso, Cargando } from '../../comun/ui/Aviso';
import { Boton } from '../../comun/ui/Boton';
import { Insignia } from '../../comun/ui/Insignia';
import { EncabezadoVista, Panel } from '../../comun/ui/Panel';
import { Pestanas } from '../../comun/ui/Pestanas';
import { TABLA, Tabla } from '../../comun/ui/Tabla';
import { useToast } from '../../comun/ui/Toast';
import { useDatos } from '../../comun/useDatos';
import { InsigniaGravedad } from '../alertas/etiquetas';
import type { Severidad } from '../alertas/tipos';
import { pacientesApi } from '../pacientes/pacientesApi';
import { useSesion } from '../sesion/SesionContext';
import { calendarioApi } from './calendarioApi';
import { DIAS, type Calendario, type DiaHabil, type DiaSemana, type Simulacion } from './tipos';

/** Pestañas del Calendario, como el mockup. */
type PestanaCalendario = 'TURNOS' | 'SIMULADOR' | 'JORNADA';

/** Día de la semana de cada getDay() (0 = domingo). */
const DIA_DE_GETDAY: DiaSemana[] = ['DOMINGO', 'LUNES', 'MARTES', 'MIERCOLES', 'JUEVES', 'VIERNES', 'SABADO'];

/** @returns el día de la semana de una fecha "2026-11-25" */
function diaDe(fecha: string): DiaSemana {
  return DIA_DE_GETDAY[new Date(`${fecha}T12:00:00Z`).getUTCDay()];
}

/** @returns la hora actual en Chile, "15:42" */
function horaEnChile(): string {
  return new Intl.DateTimeFormat('es-CL', {
    timeZone: 'America/Santiago',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date());
}

/** Tramos del día, para la tarjeta y la matriz. */
type Tramo = 'HABIL' | 'FUERA' | 'SIN_JORNADA';

/** @returns el tramo de ese día y hora según el calendario: hábil, fuera de horario o sin jornada (fin de semana o feriado) */
function tramoDe(calendario: Calendario, fecha: string, hora: string): Tramo {
  if (calendario.feriados.some((f) => f.fecha === fecha)) return 'SIN_JORNADA';
  const jornada = calendario.horarioHabil.find((d) => d.dia === diaDe(fecha));
  if (!jornada) return 'SIN_JORNADA';
  return hora >= jornada.inicio && hora < jornada.fin ? 'HABIL' : 'FUERA';
}

/**
 * Calendario del mockup, con las reglas que el sistema aplica hoy: turnos (el tramo vigente y qué pasa con cada
 * gravedad), simulador de canales y jornada hábil con sus feriados. La jornada la edita quien tiene
 * ADMINISTRACION:EDITAR; el simulador lo usa quien ve alertas.
 */
export function PaginaCalendario() {
  const { puede } = useSesion();
  const { datos, error, cargando, recargar } = useDatos(calendarioApi.consultar);
  const [pestana, setPestana] = useState<PestanaCalendario>('TURNOS');

  const pestanas = [
    { clave: 'TURNOS' as const, texto: 'Turnos activos' },
    ...(puede('ALERTAS:VER') ? [{ clave: 'SIMULADOR' as const, texto: 'Simulador de canales' }] : []),
    { clave: 'JORNADA' as const, texto: 'Jornada hábil y feriados' },
  ];

  return (
    <>
      <EncabezadoVista
        titulo="Calendario"
        subtitulo="Turnos de atención, simulador de canales y configuración de la jornada hábil PRLAC."
      />
      <Pestanas etiqueta="Secciones del calendario" pestanas={pestanas} activa={pestana} alCambiar={setPestana} />
      {error && <Aviso tono="error">{error}</Aviso>}
      {cargando && !datos && <Cargando />}
      {datos && pestana === 'TURNOS' && <Turnos calendario={datos} />}
      {datos && pestana === 'SIMULADOR' && <Simulador />}
      {datos && pestana === 'JORNADA' && <Jornada calendario={datos} alGuardar={recargar} />}
    </>
  );
}

/** Qué pasa con cada gravedad en cada tramo, según las reglas de hoy. */
const REGLAS: Record<Tramo, { grave: ReactNode; leve: ReactNode }> = {
  HABIL: {
    grave: (
      <Pasos
        pasos={['Oficina PRLAC: correo y WhatsApp a los coordinadores', 'El reloj avisa a los contactos del usuario, por prioridad']}
        nota="Los dos al mismo tiempo. El reloj espera la respuesta de cada contacto antes de pasar al siguiente."
      />
    ),
    leve: (
      <Pasos
        pasos={['Oficina PRLAC: correo y WhatsApp a los coordinadores', 'El reloj avisa a los contactos del usuario, por prioridad']}
        nota="Igual que una grave, pero sin la indicación de llamar al 131."
      />
    ),
  },
  FUERA: {
    grave: (
      <Pasos
        pasos={['El reloj avisa a los contactos disponibles a esa hora, por prioridad']}
        nota="La oficina PRLAC no recibe aviso fuera de horario: la alerta queda en el dashboard."
      />
    ),
    leve: (
      <Pasos pasos={['No se avisa a nadie: espera al próximo día hábil']} nota="Queda en el dashboard como pendiente del día hábil." espera />
    ),
  },
  SIN_JORNADA: {
    grave: (
      <Pasos
        pasos={['El reloj avisa a los contactos disponibles a esa hora, por prioridad']}
        nota="Fin de semana o feriado: la oficina PRLAC no recibe aviso."
      />
    ),
    leve: (
      <Pasos pasos={['No se avisa a nadie: espera al próximo día hábil']} nota="Queda en el dashboard como pendiente del día hábil." espera />
    ),
  },
};

/** Lista numerada de pasos de una celda de la matriz. */
function Pasos({ pasos, nota, espera = false }: { pasos: string[]; nota: string; espera?: boolean }) {
  return (
    <div className={`rounded-panel border p-3 ${espera ? 'border-dashed border-naranja bg-[#fffaf4]' : 'border-linea bg-superficie'}`}>
      <ol className="space-y-2">
        {pasos.map((paso, i) => (
          <li key={paso} className="flex gap-2 text-[12.5px]">
            <span className="cifra grid h-[22px] w-[22px] flex-none place-items-center rounded-full bg-navy text-[11px] font-bold text-white">
              {i + 1}°
            </span>
            <span className="pt-0.5">{paso}</span>
          </li>
        ))}
      </ol>
      <p className="mt-2 border-t border-linea pt-2 text-[11.5px] text-apagado">{nota}</p>
    </div>
  );
}

/** Nombre de cada tramo. */
const NOMBRE_TRAMO: Record<Tramo, string> = {
  HABIL: 'Jornada hábil PRLAC',
  FUERA: 'Fuera del horario hábil',
  SIN_JORNADA: 'Sin jornada (fin de semana o feriado)',
};

/** Tarjeta del día actual (se actualiza cada minuto) y matriz semanal de cobertura. */
function Turnos({ calendario }: { calendario: Calendario }) {
  const [ahora, setAhora] = useState(() => ({ fecha: hoyEnChile(), hora: horaEnChile() }));
  useEffect(() => {
    const intervalo = window.setInterval(() => setAhora({ fecha: hoyEnChile(), hora: horaEnChile() }), 60_000);
    return () => window.clearInterval(intervalo);
  }, []);
  const tramo = tramoDe(calendario, ahora.fecha, ahora.hora);
  const jornadaHoy = calendario.horarioHabil.find((d) => d.dia === diaDe(ahora.fecha));
  const feriadoHoy = calendario.feriados.find((f) => f.fecha === ahora.fecha);
  const nombreDia = DIAS.find((d) => d.dia === diaDe(ahora.fecha))?.nombre;

  const columnas: { tramo: Tramo; titulo: string; detalle: string }[] = [
    { tramo: 'HABIL', titulo: 'Horario hábil', detalle: 'Según la jornada de cada día' },
    { tramo: 'FUERA', titulo: 'Fuera de horario', detalle: 'Días hábiles, antes o después de la jornada' },
    { tramo: 'SIN_JORNADA', titulo: 'Fines de semana y feriados', detalle: 'Días sin jornada, las 24 horas' },
  ];

  return (
    <div className="space-y-5">
      <section className="rounded-panel border border-linea border-l-[5px] border-l-teal bg-superficie p-5">
        <p className="flex items-center gap-2 text-[11px] font-bold tracking-wide text-apagado uppercase">
          <span aria-hidden="true" className="h-2.5 w-2.5 rounded-full bg-verde shadow-[0_0_0_4px] shadow-verde/20" />
          Día actual · {nombreDia} {formatearFecha(ahora.fecha)} (<span className="cifra">{ahora.hora}</span> hrs)
        </p>
        <h2 className="mt-1 text-lg font-bold text-navy">{NOMBRE_TRAMO[tramo]}</h2>
        <p className="text-[13px] text-apagado">
          {feriadoHoy
            ? `Hoy es feriado: ${feriadoHoy.nombre}.`
            : jornadaHoy
              ? `Jornada de hoy: ${jornadaHoy.inicio} a ${jornadaHoy.fin} hrs.`
              : 'Hoy no hay jornada hábil.'}
        </p>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div>
            <p className="mb-1.5 flex items-center gap-2 text-xs font-bold text-navy">
              Responde ahora <InsigniaGravedad severidad="GRAVE" /> <InsigniaGravedad severidad="MEDIA" />
            </p>
            {REGLAS[tramo].grave}
          </div>
          <div>
            <p className="mb-1.5 flex items-center gap-2 text-xs font-bold text-navy">
              Responde ahora <InsigniaGravedad severidad="LEVE" />
            </p>
            {REGLAS[tramo].leve}
          </div>
        </div>
      </section>

      <Panel titulo="Matriz semanal de cobertura">
        <div className="grid gap-3 lg:grid-cols-[160px_repeat(3,minmax(0,1fr))]">
          <div className="hidden lg:block" />
          {columnas.map((c) => (
            <div key={c.tramo} className={`rounded-panel border px-3 py-2 ${c.tramo === tramo ? 'border-teal bg-teal-tinte' : 'border-linea bg-superficie-sub'}`}>
              <p className="text-[13px] font-bold text-navy">{c.titulo}</p>
              <p className="text-[11.5px] text-apagado">{c.detalle}</p>
              {c.tramo === tramo && (
                <span className="mt-1 inline-block rounded-[3px] bg-teal px-1.5 py-px text-[10px] font-bold text-white">TRAMO ACTUAL</span>
              )}
            </div>
          ))}
          <FilaMatriz titulo={<InsigniaGravedad severidad="GRAVE" />} detalle="Grave o media" celdas={columnas.map((c) => REGLAS[c.tramo].grave)} />
          <FilaMatriz titulo={<InsigniaGravedad severidad="LEVE" />} detalle="Leve" celdas={columnas.map((c) => REGLAS[c.tramo].leve)} />
        </div>
        <p className="mt-3 text-[11.5px] text-apagado">
          Son las reglas que el sistema aplica hoy. El horario de cada contacto se respeta siempre, y con el botón de
          ayuda el reloj avisa primero a los vecinos.
        </p>
      </Panel>
    </div>
  );
}

/** Una fila de la matriz: la gravedad y una celda por tramo. */
function FilaMatriz({ titulo, detalle, celdas }: { titulo: ReactNode; detalle: string; celdas: ReactNode[] }) {
  return (
    <>
      <div className="flex flex-col justify-center gap-1 pt-2 lg:pt-0">
        {titulo}
        <span className="text-[11.5px] text-apagado">{detalle}</span>
      </div>
      {celdas.map((celda, i) => (
        <div key={i}>{celda}</div>
      ))}
    </>
  );
}

/** Simulador de canales: elige día, hora, gravedad y (opcional) un usuario, y muestra qué pasaría. */
function Simulador() {
  const { puede } = useSesion();
  const usuarios = useDatos(() => (puede('USUARIOS:VER') ? pacientesApi.listar(false) : Promise.resolve([])));
  const [fecha, setFecha] = useState(hoyEnChile());
  const [hora, setHora] = useState(horaEnChile());
  const [gravedad, setGravedad] = useState<Severidad>('GRAVE');
  const [idPaciente, setIdPaciente] = useState('');
  const [resultado, setResultado] = useState<Simulacion | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [simulando, setSimulando] = useState(false);

  /** Pide la simulación al backend. */
  async function simular(evento?: FormEvent) {
    evento?.preventDefault();
    setSimulando(true);
    setError(null);
    try {
      setResultado(await calendarioApi.simular(fecha, hora, gravedad, idPaciente || null));
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setSimulando(false);
    }
  }

  // Simula de inmediato con el día y la hora actuales
  useEffect(() => {
    void simular();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const nombreUsuario = useMemo(() => {
    const fila = usuarios.datos?.find((u) => u.paciente.id === idPaciente);
    return fila ? `${fila.paciente.datos.nombres} ${fila.paciente.datos.apellidos}` : null;
  }, [usuarios.datos, idPaciente]);

  return (
    <Panel titulo="Simulador y detalle de canales">
      <p className="mb-4 text-[13px] text-apagado">
        Comprueba a quién se avisa y por qué vía según el día, la hora y la gravedad del evento, con las reglas que el
        sistema aplica hoy.
      </p>
      <form onSubmit={simular} className="mb-4 flex flex-wrap items-end gap-3">
        <label className="block w-44">
          <span className={CLASE_ETIQUETA}>Día</span>
          <input type="date" value={fecha} onChange={(e) => setFecha(e.target.value)} className={CLASE_CAMPO} required />
        </label>
        <label className="block w-32">
          <span className={CLASE_ETIQUETA}>Hora del evento</span>
          <input type="time" value={hora} step={900} onChange={(e) => setHora(e.target.value)} className={CLASE_CAMPO} required />
        </label>
        <label className="block w-36">
          <span className={CLASE_ETIQUETA}>Gravedad</span>
          <select value={gravedad} onChange={(e) => setGravedad(e.target.value as Severidad)} className={CLASE_CAMPO}>
            <option value="GRAVE">Grave</option>
            <option value="MEDIA">Media</option>
            <option value="LEVE">Leve</option>
          </select>
        </label>
        {puede('USUARIOS:VER') && (
          <label className="block min-w-56 flex-1">
            <span className={CLASE_ETIQUETA}>Probar con persona usuaria</span>
            <select value={idPaciente} onChange={(e) => setIdPaciente(e.target.value)} className={CLASE_CAMPO}>
              <option value="">Sin usuario (solo las reglas)</option>
              {(usuarios.datos ?? []).map((u) => (
                <option key={u.paciente.id} value={u.paciente.id}>
                  {u.paciente.datos.nombres} {u.paciente.datos.apellidos}
                </option>
              ))}
            </select>
          </label>
        )}
        <Boton type="submit" variante="principal" disabled={simulando}>
          {simulando ? 'Simulando…' : 'Simular'}
        </Boton>
        <Boton
          variante="contorno"
          onClick={() => {
            setFecha(hoyEnChile());
            setHora(horaEnChile());
          }}
        >
          Volver a ahora
        </Boton>
      </form>

      {error && <Aviso tono="error">{error}</Aviso>}
      {resultado && <ResultadoSimulacion resultado={resultado} gravedad={gravedad} conUsuario={nombreUsuario} />}
    </Panel>
  );
}

/** Nombre de cada canal. */
const CANAL: Record<string, string> = { WHATSAPP: 'WhatsApp', CORREO: 'Correo' };

/** La cadena de avisos simulada. */
function ResultadoSimulacion({
  resultado,
  gravedad,
  conUsuario,
}: {
  resultado: Simulacion;
  gravedad: Severidad;
  conUsuario: string | null;
}) {
  return (
    <div className="space-y-3">
      <p className="flex flex-wrap items-center gap-2 text-[13px]">
        <InsigniaGravedad severidad={gravedad} />
        <Insignia tono={resultado.habil ? 'teal' : 'neutra'}>
          {resultado.habil ? 'Horario hábil' : resultado.feriado ? 'Feriado' : 'Fuera del horario hábil'}
        </Insignia>
        {conUsuario && <span className="text-apagado">· {conUsuario}</span>}
      </p>

      {resultado.esperaDiaHabil ? (
        <Aviso tono="advertencia" titulo="No se avisa a nadie">
          Una alerta leve fuera del horario hábil espera al próximo día hábil. Queda en el dashboard como pendiente.
        </Aviso>
      ) : (
        <ol className="space-y-2">
          {resultado.avisoOficina && (
            <Eslabon numero="•" titulo="Oficina PRLAC (coordinadores)" detalle="Correo y WhatsApp a todos a la vez, en paralelo al reloj" />
          )}
          {resultado.contactos.map((c) => (
            <Eslabon
              key={c.orden}
              numero={`${c.orden}°`}
              titulo={`${c.nombre}${c.cuidadorPrincipal ? ' · cuidador/a principal' : ''}`}
              detalle={`${c.parentesco ?? c.tipo.toLowerCase().replace('_', ' ')} · Vía: ${c.canales.map((canal) => CANAL[canal]).join(' o ')}`}
            />
          ))}
        </ol>
      )}

      {!resultado.esperaDiaHabil && !resultado.avisoOficina && (
        <Aviso tono="info">Fuera del horario hábil la oficina PRLAC no recibe la alerta en el momento: queda en el dashboard.</Aviso>
      )}
      {!conUsuario && !resultado.esperaDiaHabil && (
        <Aviso tono="info">Elige una persona usuaria para ver a qué contactos suyos avisaría el reloj, en orden.</Aviso>
      )}
      {conUsuario && !resultado.esperaDiaHabil && resultado.contactos.length === 0 && (
        <Aviso tono="advertencia">Ningún contacto de este usuario está disponible a esa hora con un canal usable.</Aviso>
      )}
    </div>
  );
}

/** Un paso de la cadena de avisos. */
function Eslabon({ numero, titulo, detalle }: { numero: string; titulo: string; detalle: string }) {
  return (
    <li className="flex gap-3 rounded-panel border border-linea bg-superficie px-3 py-2.5">
      <span className="cifra grid h-[26px] w-[26px] flex-none place-items-center rounded-full bg-navy text-[11px] font-bold text-white">{numero}</span>
      <span>
        <b className="block text-[13px] text-navy">{titulo}</b>
        <span className="text-[12px] text-apagado">{detalle}</span>
      </span>
    </li>
  );
}

/** Horario de cada día y feriados; editable con ADMINISTRACION:EDITAR. */
function Jornada({ calendario, alGuardar }: { calendario: Calendario; alGuardar: () => void }) {
  const { puede } = useSesion();
  const avisar = useToast();
  const editable = puede('ADMINISTRACION:EDITAR');
  const [editando, setEditando] = useState(false);
  const inicial = () =>
    Object.fromEntries(
      DIAS.map(({ dia }) => {
        const tramo = calendario.horarioHabil.find((d) => d.dia === dia);
        return [dia, { habil: !!tramo, inicio: tramo?.inicio ?? '08:30', fin: tramo?.fin ?? '17:30' }];
      }),
    ) as Record<DiaSemana, { habil: boolean; inicio: string; fin: string }>;
  const [dias, setDias] = useState(inicial);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  /** Guarda la jornada nueva. */
  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    const horario: DiaHabil[] = DIAS.filter(({ dia }) => dias[dia].habil).map(({ dia }) => ({
      dia,
      inicio: dias[dia].inicio,
      fin: dias[dia].fin,
    }));
    setGuardando(true);
    setError(null);
    try {
      await calendarioApi.guardarHorario(horario);
      avisar('Jornada hábil guardada: rige desde ahora');
      setEditando(false);
      alGuardar();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGuardando(false);
    }
  }

  const hoy = hoyEnChile();
  return (
    <div className="grid items-start gap-5 lg:grid-cols-2">
      <Panel
        titulo="Configuración de jornada hábil PRLAC"
        acciones={
          editable &&
          !editando && (
            <Boton
              variante="contorno"
              chico
              onClick={() => {
                setDias(inicial());
                setEditando(true);
              }}
            >
              Editar jornada
            </Boton>
          )
        }
      >
        {editando ? (
          <form onSubmit={guardar} className="space-y-3" noValidate>
            {DIAS.map(({ dia, nombre }) => (
              <div key={dia} className="flex flex-wrap items-center gap-3">
                <label className="flex w-32 items-center gap-2 text-[13px] font-medium text-navy">
                  <input
                    type="checkbox"
                    checked={dias[dia].habil}
                    onChange={(e) => setDias({ ...dias, [dia]: { ...dias[dia], habil: e.target.checked } })}
                  />
                  {nombre}
                </label>
                <label className="flex items-center gap-1.5 text-xs text-apagado">
                  Inicio
                  <input
                    type="time"
                    value={dias[dia].inicio}
                    disabled={!dias[dia].habil}
                    onChange={(e) => setDias({ ...dias, [dia]: { ...dias[dia], inicio: e.target.value } })}
                    className={`${CLASE_CAMPO} mt-0 w-28`}
                  />
                </label>
                <label className="flex items-center gap-1.5 text-xs text-apagado">
                  Término
                  <input
                    type="time"
                    value={dias[dia].fin}
                    disabled={!dias[dia].habil}
                    onChange={(e) => setDias({ ...dias, [dia]: { ...dias[dia], fin: e.target.value } })}
                    className={`${CLASE_CAMPO} mt-0 w-28`}
                  />
                </label>
              </div>
            ))}
            {error && <Aviso tono="error">{error}</Aviso>}
            <Aviso tono="info">
              El cambio rige al tiro: define qué alertas leves esperan al día hábil y cuándo se avisa a la oficina PRLAC.
              Queda en la auditoría.
            </Aviso>
            <div className="flex gap-2">
              <Boton type="submit" variante="principal" disabled={guardando}>
                {guardando ? 'Guardando…' : 'Guardar jornada'}
              </Boton>
              <Boton variante="contorno" onClick={() => setEditando(false)} disabled={guardando}>
                Cancelar
              </Boton>
            </div>
          </form>
        ) : (
          <Tabla columnas={['Día', 'Jornada hábil']} etiqueta="Jornada hábil">
            {DIAS.map(({ dia, nombre }) => {
              const tramo = calendario.horarioHabil.find((d) => d.dia === dia);
              return (
                <tr key={dia} className={TABLA.fila}>
                  <td className={`${TABLA.td} font-medium text-navy`}>{nombre}</td>
                  <td className={TABLA.td}>
                    {tramo ? (
                      <span className="cifra">
                        {tramo.inicio} a {tramo.fin} hrs
                      </span>
                    ) : (
                      <span className="text-apagado">No hábil</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </Tabla>
        )}
      </Panel>

      <Panel titulo="Feriados">
        <Tabla columnas={['Fecha', 'Feriado']} etiqueta="Feriados">
          {calendario.feriados.map((feriado) => (
            <tr key={feriado.fecha} className={`${TABLA.fila} ${feriado.fecha < hoy ? 'text-apagado' : ''}`}>
              <td className={`${TABLA.td} cifra whitespace-nowrap`}>{formatearFecha(feriado.fecha)}</td>
              <td className={TABLA.td}>{feriado.nombre}</td>
            </tr>
          ))}
        </Tabla>
        <p className="mt-3 text-[11.5px] text-apagado">
          Los feriados están en la configuración del servidor (calendario.yml).
        </p>
      </Panel>
    </div>
  );
}
