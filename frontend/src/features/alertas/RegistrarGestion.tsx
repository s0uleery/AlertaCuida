import { useState, type FormEvent } from 'react';
import { ErrorApi } from '../../api/cliente';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { Aviso } from '../../comun/ui/Aviso';
import { Boton } from '../../comun/ui/Boton';
import { useToast } from '../../comun/ui/Toast';
import { useDatos } from '../../comun/useDatos';
import { alertasApi } from './alertasApi';
import type { TipoSeguimiento } from './tipos';

/** Cómo se muestra cada tipo de gestión. */
export const TIPO_SEGUIMIENTO: Record<TipoSeguimiento, string> = {
  LLAMADA: 'Llamada',
  VISITA: 'Visita',
  DERIVACION: 'Derivación',
  NOTA: 'Nota',
};

/**
 * "Registrar gestión en bitácora" del mockup: el equipo anota una llamada, una visita, una derivación o una nota.
 * Queda en la línea de tiempo como seguimiento. La nota es obligatoria salvo en la derivación, que pide el destino.
 *
 * @param alGuardar se llama después de guardar, para recargar la ficha
 */
export function RegistrarGestion({ idAlerta, alGuardar }: { idAlerta: string; alGuardar: () => void }) {
  const avisar = useToast();
  const destinos = useDatos(alertasApi.destinosDerivacion);
  const [tipo, setTipo] = useState<TipoSeguimiento>('NOTA');
  const [destino, setDestino] = useState('');
  const [nota, setNota] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [guardando, setGuardando] = useState(false);

  const esDerivacion = tipo === 'DERIVACION';
  const completo = esDerivacion ? destino !== '' : nota.trim() !== '';

  /** Guarda la gestión y limpia el formulario. */
  async function guardar(evento: FormEvent) {
    evento.preventDefault();
    if (!completo) return;
    setGuardando(true);
    setError(null);
    try {
      await alertasApi.registrarSeguimiento(idAlerta, tipo, esDerivacion ? destino : null, nota.trim() || null);
      setNota('');
      setDestino('');
      avisar(`${TIPO_SEGUIMIENTO[tipo]} registrada en la bitácora`);
      alGuardar();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setGuardando(false);
    }
  }

  return (
    <form onSubmit={guardar} className="mb-4 space-y-2.5 rounded-panel border border-linea bg-superficie-sub p-3.5" noValidate>
      <p className={CLASE_ETIQUETA}>Registrar gestión en bitácora</p>
      <div className="flex flex-wrap gap-2">
        <label className="block w-36">
          <span className="sr-only">Tipo de gestión</span>
          <select value={tipo} onChange={(e) => setTipo(e.target.value as TipoSeguimiento)} className={CLASE_CAMPO}>
            {(Object.keys(TIPO_SEGUIMIENTO) as TipoSeguimiento[]).map((t) => (
              <option key={t} value={t}>
                {TIPO_SEGUIMIENTO[t]}
              </option>
            ))}
          </select>
        </label>
        {esDerivacion && (
          <label className="block min-w-40 flex-1">
            <span className="sr-only">Destino de la derivación</span>
            <select value={destino} onChange={(e) => setDestino(e.target.value)} className={CLASE_CAMPO}>
              <option value="">Elegir destino…</option>
              {(destinos.datos ?? []).map((d) => (
                <option key={d.codigo} value={d.codigo}>
                  {d.nombre}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>
      <label className="block">
        <span className="sr-only">Nota</span>
        <textarea
          value={nota}
          onChange={(e) => setNota(e.target.value)}
          rows={2}
          maxLength={2000}
          placeholder={esDerivacion ? 'Nota (opcional)' : 'Ej: Se contacta a la cuidadora, confirma que está estable'}
          className={CLASE_CAMPO}
        />
      </label>
      {error && <Aviso tono="error">{error}</Aviso>}
      <div className="flex justify-end">
        <Boton type="submit" variante="principal" chico disabled={guardando || !completo}>
          {guardando ? 'Registrando…' : 'Registrar'}
        </Boton>
      </div>
    </form>
  );
}
