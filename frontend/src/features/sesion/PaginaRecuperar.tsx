import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { CLASE_BOTON_PRINCIPAL, CLASE_CAMPO, CLASE_ERROR, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearRun, runValido } from '../../comun/run';
import { sesionApi } from './sesionApi';

/**
 * Página pública /recuperar: la persona que olvidó su contraseña escribe su RUN y, si su cuenta está activa,
 * le llega un correo con un enlace. El mensaje final es siempre el mismo, exista o no la cuenta, para no
 * revelar quién está registrado.
 */
export function PaginaRecuperar() {
  const [run, setRun] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [enviado, setEnviado] = useState(false);

  /** Revisa el dígito verificador y pide el correo. */
  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!runValido(run)) {
      setError('Revisa el RUN: el dígito verificador no corresponde.');
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await sesionApi.recuperar(run);
      setEnviado(true);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <div className="w-full max-w-sm space-y-5 rounded-xl bg-white p-8 shadow-sm">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">AlertaCuida</h1>
          <p className="text-sm text-slate-500">Recuperar la contraseña</p>
        </div>

        {enviado ? (
          <p role="status" className="rounded-md bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
            Si el RUN corresponde a una cuenta activa, te enviamos un correo con un enlace para elegir una contraseña
            nueva. El enlace vence en una hora. Si no llega, revisa la carpeta de spam o pide ayuda a quien administra
            AlertaCuida.
          </p>
        ) : (
          <form onSubmit={enviar} className="space-y-4" noValidate>
            <p className="text-sm text-slate-600">
              Escribe tu RUN y te enviaremos un enlace al correo registrado en tu cuenta.
            </p>
            <label className="block">
              <span className={CLASE_ETIQUETA}>RUN</span>
              <input
                value={run}
                onChange={(e) => setRun(formatearRun(e.target.value))}
                placeholder="12.345.678-9"
                autoComplete="username"
                required
                className={CLASE_CAMPO}
              />
            </label>

            {error && (
              <p role="alert" className={CLASE_ERROR}>
                {error}
              </p>
            )}

            <button type="submit" disabled={enviando || !run} className={`${CLASE_BOTON_PRINCIPAL} w-full`}>
              {enviando ? 'Enviando…' : 'Enviar enlace'}
            </button>
          </form>
        )}

        <p className="text-center text-sm">
          <Link to="/login" className="text-sky-700 underline hover:text-sky-900">
            Volver a ingresar
          </Link>
        </p>
      </div>
    </main>
  );
}
