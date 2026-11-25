import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { formatearRun, runValido } from '../../comun/run';
import { Aviso } from '../../comun/ui/Aviso';
import { claseBoton } from '../../comun/ui/Boton';
import { PlantillaAcceso } from '../../navegacion/PlantillaAcceso';
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
    <PlantillaAcceso titulo="Recuperar la contraseña" subtitulo="Te enviaremos un enlace al correo registrado en tu cuenta.">

        {enviado ? (
          <Aviso tono="exito">
            Si el RUN corresponde a una cuenta activa, te enviamos un correo con un enlace para elegir una contraseña
            nueva. El enlace vence en una hora. Si no llega, revisa la carpeta de spam o pide ayuda a quien administra
            AlertaCuida.
          </Aviso>
        ) : (
          <form onSubmit={enviar} className="space-y-4" noValidate>
            <label className="block">
              <span className={CLASE_ETIQUETA}>RUN</span>
              <input
                value={run}
                onChange={(e) => setRun(formatearRun(e.target.value))}
                placeholder="12.345.678-9"
                autoComplete="username"
                required
                className={`${CLASE_CAMPO} cifra`}
              />
            </label>

            {error && <Aviso tono="error">{error}</Aviso>}

            <button type="submit" disabled={enviando || !run} className={`${claseBoton('principal')} w-full`}>
              {enviando ? 'Enviando…' : 'Enviar enlace'}
            </button>
          </form>
        )}

        <p className="text-center text-[13px]">
          <Link to="/login" className="font-bold text-teal-oscuro hover:underline">
            Volver a ingresar
          </Link>
        </p>
    </PlantillaAcceso>
  );
}
