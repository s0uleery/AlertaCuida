import { useMemo, useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { useConfirmar } from '../../comun/Confirmacion';
import { CLASE_CAMPO } from '../../comun/estilos';
import { formatearHoraReciente } from '../../comun/fechas';
import { Aviso, Cargando, Vacio } from '../../comun/ui/Aviso';
import { Boton } from '../../comun/ui/Boton';
import { Insignia, type TonoInsignia } from '../../comun/ui/Insignia';
import { Panel } from '../../comun/ui/Panel';
import { Paginacion, TABLA, Tabla, usePaginacion } from '../../comun/ui/Tabla';
import { useToast } from '../../comun/ui/Toast';
import { useDatos } from '../../comun/useDatos';
import { useSesion } from '../sesion/SesionContext';
import { DialogoTelefono, type MotivoTelefono } from './DialogoTelefono';
import { personalApi } from './personalApi';
import { nombreRol, ROLES } from './roles';
import type { EstadoCuenta, PerfilEquipo, Rol } from './tipos';

/** Cómo se muestra cada estado de la cuenta. */
const ESTADO: Record<EstadoCuenta, { texto: string; tono: TonoInsignia }> = {
  ACTIVA: { texto: 'Activo', tono: 'leve' },
  PENDIENTE: { texto: 'Pendiente de activar', tono: 'media' },
  DESACTIVADA: { texto: 'Desactivado', tono: 'cerrada' },
};

/** Rol que recibe por WhatsApp los avisos de la oficina PRLAC: necesita teléfono. */
const ROL_AVISOS_OFICINA: Rol = 'COORDINADOR';

/** Mensaje para mostrar de un error al guardar. */
function mensajeDe(e: unknown): string {
  return e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.';
}

/** true si el texto buscado está en el nombre, cargo, correo o rol (sin importar mayúsculas ni tildes). */
function coincide(perfil: PerfilEquipo, busqueda: string): boolean {
  const normalizar = (texto: string) => texto.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
  const texto = [perfil.nombre, perfil.cargo ?? '', perfil.correo, nombreRol(perfil.rol)].join(' ');
  return normalizar(texto).includes(normalizar(busqueda));
}

/**
 * Pestaña "Equipo y roles": las cuentas del equipo tal como están en la base. Quien tiene ADMINISTRACION:EDITAR
 * cambia desde la tabla el rol de los demás, su aviso técnico por correo y su celular con WhatsApp. "Agregar
 * perfil" está en el encabezado de Administración.
 */
export function PerfilesEquipo() {
  const { usuario, puede } = useSesion();
  const confirmar = useConfirmar();
  const avisar = useToast();
  const [busqueda, setBusqueda] = useState('');
  const [guardando, setGuardando] = useState<string | null>(null);
  const [errorCambio, setErrorCambio] = useState<string | null>(null);
  // Cuenta a la que se le pide el teléfono: al pasarla a coordinador sin él, o al editarlo
  const [pidiendoTelefono, setPidiendoTelefono] = useState<PerfilEquipo | null>(null);
  const [motivoTelefono, setMotivoTelefono] = useState<MotivoTelefono>('COORDINADOR');
  const [errorTelefono, setErrorTelefono] = useState<string | null>(null);
  const { datos, error, cargando, recargar } = useDatos(personalApi.listar);

  const visibles = useMemo(
    () => (datos ?? []).filter((perfil) => !busqueda.trim() || coincide(perfil, busqueda.trim())),
    [datos, busqueda],
  );

  /**
   * Cambia el rol en el backend. Si la cuenta pasa a coordinador sin teléfono, primero lo pide;
   * si no, pide confirmación.
   */
  async function cambiarRol(perfil: PerfilEquipo, rol: Rol) {
    if (rol === ROL_AVISOS_OFICINA && !perfil.tieneTelefono) {
      setErrorTelefono(null);
      setMotivoTelefono('COORDINADOR');
      setPidiendoTelefono(perfil);
      return;
    }
    const confirmado = await confirmar({
      titulo: `¿Cambiar el rol de ${perfil.nombre}?`,
      mensaje: `Pasará de ${nombreRol(perfil.rol)} a ${nombreRol(rol)}. El cambio se aplica la próxima vez que inicie sesión.`,
      textoConfirmar: 'Cambiar rol',
    });
    if (!confirmado) return;
    setErrorCambio(null);
    try {
      await guardar(perfil.id, () => personalApi.cambiarRol(perfil.id, { rol }));
      avisar('Rol cambiado');
    } catch (e) {
      setErrorCambio(mensajeDe(e));
    }
  }

  /** Guarda el teléfono del diálogo: pasa la cuenta a coordinador o solo cambia su celular. */
  async function guardarTelefono(telefono: string, aceptaWhatsapp: boolean) {
    if (!pidiendoTelefono) return;
    const id = pidiendoTelefono.id;
    setErrorTelefono(null);
    try {
      await guardar(id, () =>
        motivoTelefono === 'COORDINADOR'
          ? personalApi.cambiarRol(id, { rol: ROL_AVISOS_OFICINA, telefono, aceptaWhatsapp })
          : personalApi.cambiarTelefono(id, telefono, aceptaWhatsapp),
      );
      setPidiendoTelefono(null);
      avisar(motivoTelefono === 'COORDINADOR' ? 'Rol cambiado a Coordinador' : 'Teléfono guardado');
    } catch (e) {
      setErrorTelefono(mensajeDe(e));
    }
  }

  /** Enciende o apaga el correo de las alertas técnicas de la cuenta. */
  async function cambiarAvisos(perfil: PerfilEquipo) {
    setErrorCambio(null);
    try {
      await guardar(perfil.id, () => personalApi.cambiarRecibeAvisos(perfil.id, !perfil.recibeAvisos));
    } catch (e) {
      setErrorCambio(mensajeDe(e));
    }
  }

  /** Marca la fila como ocupada mientras corre el cambio y recarga la tabla si resultó. */
  async function guardar(id: string, cambio: () => Promise<void>) {
    setGuardando(id);
    try {
      await cambio();
      recargar();
    } finally {
      setGuardando(null);
    }
  }

  const editable = puede('ADMINISTRACION:EDITAR');
  const paginacion = usePaginacion(visibles, 10);

  return (
    <Panel titulo="Equipo y roles">
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <input
          value={busqueda}
          onChange={(e) => setBusqueda(e.target.value)}
          placeholder="Buscar por nombre, cargo, correo o rol"
          aria-label="Buscar"
          className={`${CLASE_CAMPO} mt-0 max-w-sm`}
        />
        {datos && (
          <span className="text-[12.5px] text-apagado">
            {visibles.length} de {datos.length} cuentas
          </span>
        )}
      </div>

      {error && <Aviso tono="error">{error}</Aviso>}
      {errorCambio && <Aviso tono="error">{errorCambio}</Aviso>}
      {cargando && !datos && <Cargando />}
      {!cargando && !error && visibles.length === 0 && (
        <Vacio>{busqueda ? 'Nadie coincide con la búsqueda.' : 'Todavía no hay cuentas del equipo.'}</Vacio>
      )}

      {visibles.length > 0 && (
        <>
          <Tabla
            columnas={[
              'Funcionario/a',
              'Cargo',
              'Rol',
              'Estado',
              'Último acceso',
              'Avisos técnicos',
              editable ? 'Celular' : null,
            ]}
            etiqueta="Perfiles del equipo"
          >
            {paginacion.visibles.map((perfil) => {
              const propio = perfil.id === usuario?.id;
              return (
                <tr key={perfil.id} className={TABLA.fila}>
                  <td className={TABLA.td}>
                    <span className="flex items-center gap-2.5">
                      <span
                        aria-hidden="true"
                        className="grid h-8 w-8 flex-none place-items-center rounded-full bg-navy text-[11px] font-bold text-white"
                      >
                        {iniciales(perfil.nombre)}
                      </span>
                      <span>
                        <b className="text-navy">{perfil.nombre}</b>
                        {propio && <span className="ml-1 text-apagado">(tú)</span>}
                        <span className="block text-xs text-apagado">{perfil.correo}</span>
                      </span>
                    </span>
                  </td>
                  <td className={TABLA.td}>{perfil.cargo || <span className="text-apagado">—</span>}</td>
                  <td className={TABLA.td}>
                    {editable ? (
                      <select
                        value={perfil.rol}
                        onChange={(e) => cambiarRol(perfil, e.target.value as Rol)}
                        disabled={propio || guardando === perfil.id}
                        title={propio ? 'No puedes cambiar tu propio rol' : undefined}
                        aria-label={`Rol de ${perfil.nombre}`}
                        className={`${CLASE_CAMPO} mt-0 w-full max-w-48 py-1.5`}
                      >
                        {ROLES.map((rol) => (
                          <option key={rol.valor} value={rol.valor}>
                            {rol.etiqueta}
                          </option>
                        ))}
                      </select>
                    ) : (
                      nombreRol(perfil.rol)
                    )}
                  </td>
                  <td className={TABLA.td}>
                    <Insignia tono={ESTADO[perfil.estado].tono}>{ESTADO[perfil.estado].texto}</Insignia>
                  </td>
                  <td className={`${TABLA.td} cifra whitespace-nowrap`}>
                    {perfil.ultimoAcceso ? (
                      formatearHoraReciente(perfil.ultimoAcceso)
                    ) : (
                      <span className="text-apagado">Nunca</span>
                    )}
                  </td>
                  <td className={TABLA.td}>
                    {editable ? (
                      <Interruptor
                        encendido={perfil.recibeAvisos}
                        deshabilitado={guardando === perfil.id}
                        etiqueta={`Avisos técnicos por correo para ${perfil.nombre}`}
                        alCambiar={() => cambiarAvisos(perfil)}
                      />
                    ) : perfil.recibeAvisos ? (
                      'Sí'
                    ) : (
                      <span className="text-apagado">No</span>
                    )}
                  </td>
                  {editable && (
                    <td className={TABLA.td}>
                      <Boton
                        variante="enlace"
                        chico
                        disabled={guardando === perfil.id}
                        onClick={() => {
                          setErrorTelefono(null);
                          setMotivoTelefono('EDITAR');
                          setPidiendoTelefono(perfil);
                        }}
                        aria-label={`Editar el celular de ${perfil.nombre}`}
                      >
                        {perfil.tieneTelefono ? 'Cambiar' : 'Agregar'}
                      </Boton>
                    </td>
                  )}
                </tr>
              );
            })}
          </Tabla>
          <Paginacion estado={paginacion} />
        </>
      )}

      {editable && (
        <p className="mt-3 text-xs text-apagado">
          Avisos técnicos: correo cuando un reloj tiene un problema (deja de enviar datos, batería baja, no se lo ponen
          o envía demasiadas alertas). Solo en horario laboral. Las alertas de salud las avisa el reloj a los contactos
          de cada usuario.
        </p>
      )}

      {pidiendoTelefono && (
        <DialogoTelefono
          nombre={pidiendoTelefono.nombre}
          motivo={motivoTelefono}
          error={errorTelefono}
          guardando={guardando === pidiendoTelefono.id}
          alGuardar={guardarTelefono}
          alCancelar={() => setPidiendoTelefono(null)}
        />
      )}
    </Panel>
  );
}

/** @returns las iniciales del nombre (dos como máximo) */
function iniciales(nombre: string): string {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((parte) => parte[0]?.toUpperCase())
    .join('');
}

/** Propiedades de Interruptor. */
interface PropsInterruptor {
  encendido: boolean;
  deshabilitado: boolean;
  /** Para lectores de pantalla: qué se enciende */
  etiqueta: string;
  alCambiar: () => void;
}

/** Interruptor de encendido y apagado, con el color del PRLAC cuando está encendido. */
function Interruptor({ encendido, deshabilitado, etiqueta, alCambiar }: PropsInterruptor) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={encendido}
      aria-label={etiqueta}
      disabled={deshabilitado}
      onClick={alCambiar}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors disabled:opacity-50 ${
        encendido ? 'bg-teal' : 'bg-linea-oscura'
      }`}
    >
      <span
        className={`inline-block size-5 rounded-full bg-white shadow transition-transform ${
          encendido ? 'translate-x-5.5' : 'translate-x-0.5'
        }`}
      />
    </button>
  );
}
