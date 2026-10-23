import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { CLASE_BOTON_PRINCIPAL, CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearRun, runValido } from '../../comun/run';
import { useSesion } from './SesionContext';

/**
 * Ingreso con RUN y contraseña. Revisa el dígito verificador antes de enviar; si ya hay sesión,
 * vuelve a la página desde donde se llegó (o al inicio).
 */
export function PaginaLogin() {
  const { usuario, iniciar } = useSesion();
  const navegar = useNavigate();
  const destino = (useLocation().state as { desde?: string } | null)?.desde ?? '/';

  const [run, setRun] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  if (usuario) {
    return <Navigate to={destino} replace />;
  }

  /** Revisa el RUN, inicia sesión y vuelve a la página de origen. */
  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!runValido(run)) {
      setError('Revisa el RUN: el dígito verificador no corresponde.');
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await iniciar(run, contrasena);
      navegar(destino, { replace: true });
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <form onSubmit={enviar} className="w-full max-w-sm space-y-5 rounded-xl bg-white p-8 shadow-sm" noValidate>
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">AlertaCuida</h1>
          <p className="text-sm text-slate-500">Ingreso del personal del PRLAC</p>
        </div>

        <label className="block">
          <span className={CLASE_ETIQUETA}>RUN</span>
          <input
            value={run}
            onChange={(e) => setRun(formatearRun(e.target.value))}
            placeholder="12.345.678-9"
            autoComplete="username"
            inputMode="text"
            required
            className={CLASE_CAMPO}
          />
        </label>

        <label className="block">
          <span className={CLASE_ETIQUETA}>Contraseña</span>
          <input
            type="password"
            value={contrasena}
            onChange={(e) => setContrasena(e.target.value)}
            autoComplete="current-password"
            required
            className={CLASE_CAMPO}
          />
        </label>

        {error && (
          <p role="alert" className={CLASE_ERROR}>
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={enviando || !run || !contrasena}
          className={`${CLASE_BOTON_PRINCIPAL} w-full`}
        >
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>
      </form>
    </main>
  );
}
