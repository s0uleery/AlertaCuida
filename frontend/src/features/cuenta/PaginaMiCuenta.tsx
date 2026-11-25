import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_BOTON_PRINCIPAL, CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA, CLASE_TARJETA } from '../../comun/estilos';
import { useDatos } from '../../comun/useDatos';
import { cuentaApi, type MiCuenta } from './cuentaApi';

/**
 * "Mi cuenta" (/mi-cuenta): cada persona del equipo ve sus datos y enciende o apaga sus avisos por WhatsApp
 * (decisión del 24-11-2026). Se llega desde el nombre en la barra superior.
 */
export function PaginaMiCuenta() {
  const { datos, error, cargando, recargar } = useDatos(cuentaApi.ver);

  return (
    <section className={`${CLASE_TARJETA} max-w-xl space-y-4`}>
      <h1 className="text-2xl font-medium text-tinta">Mi cuenta</h1>
      {error && <p className={CLASE_ERROR}>{error}</p>}
      {cargando && !datos && <p className="text-slate-500">Cargando…</p>}
      {datos && (
        <>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
            <dt className="text-slate-500">Nombre</dt>
            <dd className="text-slate-900">{datos.nombre}</dd>
            <dt className="text-slate-500">Correo</dt>
            <dd className="text-slate-900">{datos.correo ?? 'Sin correo'}</dd>
          </dl>
          {/* La clave obliga a reiniciar el formulario con los datos guardados después de cada cambio */}
          <AvisosWhatsapp key={`${datos.telefono}-${datos.whatsapp}`} cuenta={datos} alGuardar={recargar} />
        </>
      )}
    </section>
  );
}

/** Teléfono e interruptor de WhatsApp, con un solo botón para guardar. */
function AvisosWhatsapp({ cuenta, alGuardar }: { cuenta: MiCuenta; alGuardar: () => void }) {
  const [telefono, setTelefono] = useState(cuenta.telefono ?? '');
  const [whatsapp, setWhatsapp] = useState(cuenta.whatsapp);
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [guardado, setGuardado] = useState(false);

  const cambioNumero = cuenta.whatsapp && telefono.trim() !== (cuenta.telefono ?? '');
  const sinCambios = telefono.trim() === (cuenta.telefono ?? '') && whatsapp === cuenta.whatsapp;

  /** Guarda el teléfono y el WhatsApp; el backend revisa el número. */
  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    setGuardando(true);
    setError(null);
    setGuardado(false);
    try {
      await cuentaApi.cambiarWhatsapp(telefono.trim(), whatsapp);
      setGuardado(true);
      alGuardar();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={guardar} className="space-y-4 border-t border-slate-200 pt-4" noValidate>
      <h2 className="text-lg font-medium text-tinta">Avisos por WhatsApp</h2>
      <p className="text-sm text-slate-600">
        Los avisos siempre te llegan también por correo. El WhatsApp es un canal adicional, que puedes apagar
        cuando quieras.
      </p>

      <label className="block">
        <span className={CLASE_ETIQUETA}>Celular con WhatsApp</span>
        <input
          value={telefono}
          onChange={(e) => setTelefono(e.target.value)}
          placeholder="+56 9 1234 5678"
          inputMode="tel"
          autoComplete="tel"
          className={CLASE_CAMPO}
        />
        {cambioNumero && (
          <span className="mt-1 block text-xs text-amber-800">
            Al cambiar el número, guardar vuelve a activar el WhatsApp con el número nuevo.
          </span>
        )}
      </label>

      <label className="flex items-center gap-2 text-sm text-slate-700">
        <input type="checkbox" checked={whatsapp} onChange={(e) => setWhatsapp(e.target.checked)} />
        Recibir avisos por WhatsApp en este número
      </label>

      {error && (
        <p role="alert" className={CLASE_ERROR}>
          {error}
        </p>
      )}
      {guardado && !error && (
        <p role="status" className="rounded-md bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
          Listo, se guardó el cambio.
        </p>
      )}

      <button type="submit" disabled={guardando || sinCambios} className={CLASE_BOTON_PRINCIPAL}>
        {guardando ? 'Guardando…' : 'Guardar'}
      </button>
    </form>
  );
}
