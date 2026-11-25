import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { Aviso, Cargando } from '../../comun/ui/Aviso';
import { Boton } from '../../comun/ui/Boton';
import { EncabezadoVista, ListaDatos, Panel } from '../../comun/ui/Panel';
import { useToast } from '../../comun/ui/Toast';
import { useDatos } from '../../comun/useDatos';
import { NOMBRE_ROL } from '../../navegacion/Plantilla';
import { useSesion } from '../sesion/SesionContext';
import { cuentaApi, type MiCuenta } from './cuentaApi';

/** Largo mínimo de la contraseña (la política completa la revisa el backend). */
const LARGO_MINIMO = 12;

/**
 * "Mi perfil" (/perfil), como el mockup: ficha del funcionario, canales de aviso (el WhatsApp es opcional; el
 * correo llega siempre) y seguridad (cambiar la contraseña propia). Se llega desde el menú del nombre.
 */
export function PaginaMiCuenta() {
  const { usuario } = useSesion();
  const { datos, error, cargando, recargar } = useDatos(cuentaApi.ver);

  return (
    <>
      <EncabezadoVista titulo="Mi perfil" subtitulo="Tus datos, cómo te llegan los avisos y tu contraseña." />
      {error && <Aviso tono="error">{error}</Aviso>}
      {cargando && !datos && <Cargando />}
      {datos && (
        <div className="grid items-start gap-5 lg:grid-cols-2">
          <div className="space-y-5">
            <Panel titulo="Ficha del funcionario">
              <div className="mb-4 flex items-center gap-3">
                <span
                  aria-hidden="true"
                  className="grid h-12 w-12 place-items-center rounded-full bg-navy text-base font-bold text-white"
                >
                  {iniciales(datos.nombre)}
                </span>
                <span>
                  <b className="block text-[15px] text-navy">{datos.nombre}</b>
                  <span className="text-[12px] text-apagado">
                    {usuario ? (NOMBRE_ROL[usuario.rol] ?? usuario.rol) : ''}
                  </span>
                </span>
              </div>
              <ListaDatos
                datos={[
                  ['Nombre', datos.nombre],
                  ['Correo institucional', datos.correo ?? 'Sin correo'],
                  ['Rol', usuario ? (NOMBRE_ROL[usuario.rol] ?? usuario.rol) : '—'],
                  ['Celular', datos.telefono ?? 'Sin celular'],
                ]}
              />
              <p className="mt-3 text-[11.5px] text-apagado">
                El nombre, el correo y el rol los cambia la administración del sistema.
              </p>
            </Panel>
            {/* La clave reinicia el formulario con los datos guardados después de cada cambio */}
            <AvisosWhatsapp key={`${datos.telefono}-${datos.whatsapp}`} cuenta={datos} alGuardar={recargar} />
          </div>
          <CambiarContrasena />
        </div>
      )}
    </>
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

/** Canales de aviso: el correo siempre, y el WhatsApp con su número, que se puede apagar. */
function AvisosWhatsapp({ cuenta, alGuardar }: { cuenta: MiCuenta; alGuardar: () => void }) {
  const avisar = useToast();
  const [telefono, setTelefono] = useState(cuenta.telefono ?? '');
  const [whatsapp, setWhatsapp] = useState(cuenta.whatsapp);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const cambioNumero = cuenta.whatsapp && telefono.trim() !== (cuenta.telefono ?? '');
  const sinCambios = telefono.trim() === (cuenta.telefono ?? '') && whatsapp === cuenta.whatsapp;

  /** Guarda el teléfono y el WhatsApp; el backend revisa el número. */
  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    try {
      await cuentaApi.cambiarWhatsapp(telefono.trim(), whatsapp);
      avisar('Canales de aviso guardados');
      alGuardar();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Panel titulo="Canales de aviso">
      <form onSubmit={guardar} className="space-y-4" noValidate>
        <div className="flex items-center justify-between rounded-panel border border-linea bg-superficie-sub px-3 py-2.5">
          <span>
            <b className="block text-[13px] text-navy">Correo electrónico</b>
            <span className="text-[12px] text-apagado">{cuenta.correo ?? 'Sin correo'} · siempre activo</span>
          </span>
          <span className="rounded-[3px] bg-verde-tinte px-1.5 py-px text-[10.5px] font-bold text-verde-texto">ACTIVO</span>
        </div>

        <div className="rounded-panel border border-linea px-3 py-3">
          <label className="flex items-center justify-between gap-3">
            <span>
              <b className="block text-[13px] text-navy">WhatsApp</b>
              <span className="text-[12px] text-apagado">Canal adicional, que puedes apagar cuando quieras</span>
            </span>
            <input
              type="checkbox"
              role="switch"
              checked={whatsapp}
              onChange={(e) => setWhatsapp(e.target.checked)}
              className="h-5 w-9 accent-teal"
              aria-label="Recibir avisos por WhatsApp"
            />
          </label>
          <label className="mt-3 block">
            <span className={CLASE_ETIQUETA}>Celular con WhatsApp</span>
            <input
              value={telefono}
              onChange={(e) => setTelefono(e.target.value)}
              placeholder="+56 9 1234 5678"
              inputMode="tel"
              autoComplete="tel"
              className={`${CLASE_CAMPO} cifra`}
            />
            {cambioNumero && (
              <span className="mt-1 block text-xs text-naranja-texto">
                Al cambiar el número, guardar vuelve a activar el WhatsApp con el número nuevo.
              </span>
            )}
          </label>
        </div>

        {error && <Aviso tono="error">{error}</Aviso>}
        <Boton type="submit" variante="principal" disabled={guardando || sinCambios}>
          {guardando ? 'Guardando…' : 'Guardar canales'}
        </Boton>
      </form>
    </Panel>
  );
}

/** Seguridad: cambiar la contraseña con la actual, la nueva y su confirmación. */
function CambiarContrasena() {
  const avisar = useToast();
  const [actual, setActual] = useState('');
  const [nueva, setNueva] = useState('');
  const [confirmacion, setConfirmacion] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  /** Revisa lo básico en el navegador y deja el resto de la política al backend. */
  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    setError(null);
    if (!actual) {
      setError('Escribe tu contraseña actual.');
      return;
    }
    if (nueva.length < LARGO_MINIMO) {
      setError(`La contraseña nueva debe tener al menos ${LARGO_MINIMO} caracteres.`);
      return;
    }
    if (nueva !== confirmacion) {
      setError('La confirmación no coincide con la contraseña nueva.');
      return;
    }
    if (nueva === actual) {
      setError('La contraseña nueva debe ser distinta de la actual.');
      return;
    }
    setGuardando(true);
    try {
      await cuentaApi.cambiarContrasena(actual, nueva);
      setActual('');
      setNueva('');
      setConfirmacion('');
      avisar('Contraseña actualizada');
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGuardando(false);
    }
  }

  return (
    <Panel titulo="Seguridad">
      <form onSubmit={guardar} className="space-y-3" noValidate>
        <label className="block">
          <span className={CLASE_ETIQUETA}>Contraseña actual</span>
          <input
            type="password"
            value={actual}
            onChange={(e) => setActual(e.target.value)}
            autoComplete="current-password"
            className={CLASE_CAMPO}
          />
        </label>
        <label className="block">
          <span className={CLASE_ETIQUETA}>Contraseña nueva</span>
          <input
            type="password"
            value={nueva}
            onChange={(e) => setNueva(e.target.value)}
            autoComplete="new-password"
            className={CLASE_CAMPO}
            aria-describedby="largo-contrasena"
          />
          <span id="largo-contrasena" className="mt-1 block text-xs text-apagado">
            <span className="cifra">{nueva.length}</span> de {LARGO_MINIMO} caracteres mínimos. No puede ser tu RUN.
          </span>
        </label>
        <label className="block">
          <span className={CLASE_ETIQUETA}>Confirmar contraseña nueva</span>
          <input
            type="password"
            value={confirmacion}
            onChange={(e) => setConfirmacion(e.target.value)}
            autoComplete="new-password"
            className={CLASE_CAMPO}
          />
        </label>
        {error && <Aviso tono="error">{error}</Aviso>}
        <Aviso tono="info">
          Si escribes mal la contraseña actual, cuenta como un intento fallido: después de varios, la cuenta se
          bloquea por un rato.
        </Aviso>
        <Boton type="submit" variante="principal" disabled={guardando}>
          {guardando ? 'Guardando…' : 'Cambiar contraseña'}
        </Boton>
      </form>
    </Panel>
  );
}
