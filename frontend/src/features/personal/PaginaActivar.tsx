import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import {
  CLASE_BOTON_PRINCIPAL,
  CLASE_CAMPO,
  CLASE_ERROR,
  CLASE_ETIQUETA,
  CLASE_TARJETA,
} from '../../comun/estilos';
import { personalApi } from './personalApi';

/** Igual que PoliticaContrasena.java. Aquí solo se revisa el largo; el backend revisa todo. */
const LARGO_MINIMO = 12;

/**
 * Página pública /activar#<token>, que abre la persona nueva desde su enlace para elegir su
 * contraseña. El token va después de "#" para que nunca llegue a los logs del servidor; al leerlo
 * se borra de la barra de direcciones para que tampoco quede en el historial del navegador.
 */
export function PaginaActivar() {
  const [token] = useState(() => window.location.hash.slice(1));
  const [contrasena, setContrasena] = useState('');
  const [repetida, setRepetida] = useState('');
  const [mostrar, setMostrar] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [lista, setLista] = useState(false);

  useEffect(() => {
    if (window.location.hash) {
      window.history.replaceState(null, '', window.location.pathname);
    }
  }, []);

  /** Revisa el largo y que las dos contraseñas coincidan, y envía la contraseña al backend. */
  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (contrasena.length < LARGO_MINIMO) {
      setError(`La contraseña debe tener al menos ${LARGO_MINIMO} caracteres.`);
      return;
    }
    if (contrasena !== repetida) {
      setError('Las contraseñas no coinciden.');
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await personalApi.activar(token, contrasena);
      setLista(true);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-4">
      <div className={`${CLASE_TARJETA} w-full max-w-md space-y-5 p-8`}>
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">AlertaCuida</h1>
          <p className="text-sm text-slate-500">Activa tu cuenta del PRLAC</p>
        </div>

        {lista ? (
          <Exito />
        ) : !token ? (
          <p role="alert" className={CLASE_ERROR}>
            El enlace está incompleto. Ábrelo tal como te llegó o pide uno nuevo al administrador.
          </p>
        ) : (
          <form onSubmit={enviar} className="space-y-4" noValidate>
            <p className="text-sm text-slate-600">
              Elige una contraseña de al menos {LARGO_MINIMO} caracteres. Una frase que recuerdes es más segura
              que una palabra corta con símbolos. No puede ser tu RUN.
            </p>

            <label className="block">
              <span className={CLASE_ETIQUETA}>Contraseña nueva</span>
              <input
                type={mostrar ? 'text' : 'password'}
                value={contrasena}
                onChange={(e) => setContrasena(e.target.value)}
                autoComplete="new-password"
                required
                className={CLASE_CAMPO}
              />
              <span className="mt-1 block text-xs text-slate-500">
                {contrasena.length} de {LARGO_MINIMO} caracteres mínimos
              </span>
            </label>

            <label className="block">
              <span className={CLASE_ETIQUETA}>Repítela</span>
              <input
                type={mostrar ? 'text' : 'password'}
                value={repetida}
                onChange={(e) => setRepetida(e.target.value)}
                autoComplete="new-password"
                required
                className={CLASE_CAMPO}
              />
            </label>

            <label className="flex items-center gap-2 text-sm text-slate-600">
              <input type="checkbox" checked={mostrar} onChange={(e) => setMostrar(e.target.checked)} />
              Mostrar contraseña
            </label>

            {error && (
              <p role="alert" className={CLASE_ERROR}>
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={enviando || !contrasena || !repetida}
              className={`${CLASE_BOTON_PRINCIPAL} w-full`}
            >
              {enviando ? 'Activando…' : 'Activar cuenta'}
            </button>
          </form>
        )}
      </div>
    </main>
  );
}

/** Mensaje final, con el camino al login. */
function Exito() {
  return (
    <div className="space-y-4">
      <p className="rounded-md bg-emerald-50 px-4 py-3 text-emerald-900">
        Tu cuenta está activa. Ya puedes ingresar con tu RUN y la contraseña que elegiste.
      </p>
      <Link to="/login" className={`${CLASE_BOTON_PRINCIPAL} block w-full text-center`}>
        Ir a ingresar
      </Link>
    </div>
  );
}
