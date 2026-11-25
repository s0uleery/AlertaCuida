import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { formatearFecha, formatearFechaHora } from '../../comun/fechas';
import { formatearRun } from '../../comun/run';
import { Aviso, Cargando } from '../../comun/ui/Aviso';
import { Boton, claseBoton } from '../../comun/ui/Boton';
import { Insignia } from '../../comun/ui/Insignia';
import { EncabezadoVista, ListaDatos, Panel } from '../../comun/ui/Panel';
import { TABLA, Tabla } from '../../comun/ui/Tabla';
import { useToast } from '../../comun/ui/Toast';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { CondicionesDelUsuario } from './CondicionesDelUsuario';
import { ContactosDelUsuario } from './ContactosDelUsuario';
import { pacientesApi } from './pacientesApi';
import { PerfilClinicoDelUsuario } from './PerfilClinicoDelUsuario';
import { RelojDelUsuario } from './RelojDelUsuario';
import type { DatosPaciente } from './tipos';

/** Cómo se muestra cada sexo. */
const SEXO: Record<string, string> = { FEMENINO: 'Femenino', MASCULINO: 'Masculino' };
/** Cómo se muestra cada nivel de dependencia. */
const NIVEL: Record<string, string> = { MODERADA: 'Moderada', SEVERA: 'Severa' };

/** Datos de identificación: etiqueta y valor. */
function identificacion(datos: DatosPaciente, edad: number | null): [string, ReactNode][] {
  const fecha = (valor: string | null) => (valor ? formatearFecha(valor) : null);
  return [
    ['RUN', <span key="run" className="cifra">{formatearRun(datos.run)}</span>],
    ['Alias para los mensajes', datos.alias],
    ['Fecha de nacimiento', datos.fechaNacimiento ? `${fecha(datos.fechaNacimiento)} (${edad} años)` : null],
    ['Sexo', datos.sexo ? SEXO[datos.sexo] : 'No informado'],
    ['Dirección', datos.direccion],
    ['Dependencia', datos.nivelDependencia ? NIVEL[datos.nivelDependencia] : 'Por evaluar'],
    ['Fecha de ingreso', fecha(datos.fechaIngreso)],
    ['Consentimiento', datos.fechaConsentimiento ? fecha(datos.fechaConsentimiento) : <b key="c" className="text-naranja-texto">Sin registrar</b>],
  ];
}

/**
 * Ficha de un usuario del programa (/usuarios/:id), con el orden del mockup: "Identificación y red de cuidados"
 * (datos, contactos con su cuidador principal, reloj e historial) y "Umbrales biométricos y antecedentes"
 * (perfil clínico y condiciones de salud). Cada bloque conserva sus acciones.
 */
export function FichaUsuario() {
  const { id = '' } = useParams();
  const { puede } = useSesion();
  const confirmar = useConfirmar();
  const avisar = useToast();
  const { datos: detalle, error, cargando, recargar } = useDatos(() => pacientesApi.detalle(id), id);
  const [errorBaja, setErrorBaja] = useState<string | null>(null);

  if (cargando && !detalle) return <Cargando />;
  if (error || !detalle) return <Aviso tono="error">{error ?? 'No se encontró el usuario.'}</Aviso>;

  const { paciente, edad, historial } = detalle;
  const datos = paciente.datos;

  /** Pide confirmación, da de baja al usuario y recarga la ficha. */
  async function darDeBaja() {
    const confirmado = await confirmar({
      titulo: '¿Dar de baja a este usuario?',
      mensaje: 'Se le retirará el reloj; su historial se conserva.',
      textoConfirmar: 'Dar de baja',
      peligro: true,
    });
    if (!confirmado) return;
    try {
      await pacientesApi.darDeBaja(paciente.id);
      avisar('Usuario dado de baja');
      recargar();
    } catch (e) {
      setErrorBaja(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    }
  }

  return (
    <>
      <EncabezadoVista
        migas={[{ texto: 'Usuarios', a: '/usuarios' }]}
        titulo={
          <>
            {datos.nombres} {datos.apellidos}
            {edad !== null && `, ${edad} años`}
          </>
        }
        subtitulo={
          <>
            {datos.direccion ?? 'Sin dirección registrada'} · Ingreso {formatearFecha(datos.fechaIngreso)}
            {paciente.fechaBaja && <> · Dado de baja el {formatearFecha(paciente.fechaBaja)}</>}
          </>
        }
        acciones={
          <>
            {paciente.fechaBaja && <Insignia tono="neutra">De baja</Insignia>}
            {puede('REPORTES:VER') && (
              <Link to={`/reportes/${paciente.id}/semanal`} className={claseBoton('contorno')}>
                Ver reporte
              </Link>
            )}
            {puede('USUARIOS:EDITAR') && (
              <Link to={`/usuarios/${paciente.id}/editar`} className={claseBoton('normal')}>
                Editar ficha
              </Link>
            )}
            {puede('USUARIOS:ELIMINAR') && !paciente.fechaBaja && (
              <Boton variante="peligro" onClick={darDeBaja}>
                Dar de baja
              </Boton>
            )}
          </>
        }
      />

      {errorBaja && <Aviso tono="error" className="mb-4">{errorBaja}</Aviso>}

      <div className="grid items-start gap-5 xl:grid-cols-2">
        <div className="space-y-5">
          <Panel titulo="Identificación">
            <ListaDatos datos={identificacion(datos, edad)} />
          </Panel>
          <Panel titulo="Red de cuidados">
            <ContactosDelUsuario idPaciente={paciente.id} />
          </Panel>
          <Panel titulo="Reloj vinculado">
            <div className="space-y-4">
              <RelojDelUsuario detalle={detalle} alCambiar={recargar} />
            </div>
          </Panel>
          <Panel titulo="Historial de relojes">
            {historial.length === 0 ? (
              <p className="text-apagado">Nunca ha tenido reloj.</p>
            ) : (
              <Tabla columnas={['Reloj', 'Desde', 'Hasta']} etiqueta="Historial de relojes">
                {historial.map((asignacion) => (
                  <tr key={asignacion.idAsignacion} className={TABLA.fila}>
                    <td className={`${TABLA.td} cifra`}>{asignacion.identificadorReloj}</td>
                    <td className={`${TABLA.td} cifra`}>{formatearFechaHora(asignacion.inicio)}</td>
                    <td className={TABLA.td}>
                      {asignacion.fin ? (
                        <span className="cifra">{formatearFechaHora(asignacion.fin)}</span>
                      ) : (
                        <Insignia tono="leve">En uso</Insignia>
                      )}
                    </td>
                  </tr>
                ))}
              </Tabla>
            )}
          </Panel>
        </div>

        <div className="space-y-5">
          <Panel titulo="Umbrales biométricos y módulos">
            <PerfilClinicoDelUsuario idPaciente={paciente.id} />
          </Panel>
          <Panel titulo="Antecedentes de salud">
            <CondicionesDelUsuario idPaciente={paciente.id} />
          </Panel>
        </div>
      </div>
    </>
  );
}
