import { useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearRun, runValido } from '../../comun/run';
import { Aviso } from '../../comun/ui/Aviso';
import { claseBoton } from '../../comun/ui/Boton';
import { PlantillaAcceso } from '../../navegacion/PlantillaAcceso';
import { useSesion } from './SesionContext';

/**
 * Ingreso con RUN y contraseña. Revisa el dígito verificador antes de enviar; si ya hay sesión,
 * vuelve a la página desde donde se llegó (o al inicio).
 */
export function PaginaLogin() {
  const { usuario, iniciar, avisoCierre } = useSesion();
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
    <PlantillaAcceso titulo="Ingreso a la plataforma" subtitulo="Monitoreo y teleasistencia del PRLAC. Ingresa con tu RUN.">
      <form onSubmit={enviar} className="space-y-4" noValidate>

        {avisoCierre && !error && (
          <Aviso tono="advertencia">{avisoCierre}</Aviso>
        )}

        <label className="block">
          <span className={CLASE_ETIQUETA}>RUN</span>
          <input
            value={run}
            onChange={(e) => setRun(formatearRun(e.target.value))}
            placeholder="12.345.678-9"
            autoComplete="username"
            inputMode="text"
            required
            className={`${CLASE_CAMPO} cifra`}
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

        {error && <Aviso tono="error">{error}</Aviso>}

        <button
          type="submit"
          disabled={enviando || !run || !contrasena}
          className={`${claseBoton('principal')} w-full`}
        >
          {enviando ? 'Ingresando…' : 'Ingresar'}
        </button>

        <p className="text-center text-[13px]">
          <Link to="/recuperar" className="font-bold text-teal-oscuro hover:underline">
            ¿Olvidaste tu contraseña?
          </Link>
        </p>
      </form>
    </PlantillaAcceso>
  );
}
