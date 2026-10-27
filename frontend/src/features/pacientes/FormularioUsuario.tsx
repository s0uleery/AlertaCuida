import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { ErrorApi } from '../../api/cliente';
import {
  CLASE_BOTON_PRINCIPAL,
  CLASE_BOTON_SECUNDARIO,
  CLASE_CAMPO,
  CLASE_ERROR,
  CLASE_ETIQUETA,
  CLASE_TARJETA,
} from '../../comun/estilos';
import { hoyEnChile } from '../../comun/fechas';
import { formatearRun, runValido } from '../../comun/run';
import { pacientesApi } from './pacientesApi';
import type { DatosPaciente, NivelDependencia, Sexo } from './tipos';

/** Formulario vacío para un alta, con la fecha de ingreso de hoy. */
function vacio(): DatosPaciente {
  return {
    run: '',
    nombres: '',
    apellidos: '',
    alias: null,
    fechaNacimiento: null,
    sexo: null,
    direccion: null,
    nivelDependencia: null,
    fechaConsentimiento: null,
    fechaIngreso: hoyEnChile(),
  };
}

/**
 * Alta (/usuarios/nuevo) y edición (/usuarios/:id/editar) de la identidad de un usuario del
 * programa. Revisa el RUN antes de enviar; las demás reglas las revisa el backend.
 */
export function FormularioUsuario() {
  const { id } = useParams();
  const navegar = useNavigate();
  const [datos, setDatos] = useState<DatosPaciente | null>(id ? null : vacio());
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  // Al editar, se parte de los datos guardados
  useEffect(() => {
    if (!id) return;
    pacientesApi
      .detalle(id)
      .then((detalle) => setDatos({ ...detalle.paciente.datos, run: formatearRun(detalle.paciente.datos.run) }))
      .catch((e) => setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.'));
  }, [id]);

  if (!datos) {
    return error ? <p className={CLASE_ERROR}>{error}</p> : <p className="text-slate-500">Cargando…</p>;
  }

  /** Actualiza un campo; un texto vacío se guarda como null (salvo los obligatorios). */
  function cambiar<K extends keyof DatosPaciente>(campo: K, valor: DatosPaciente[K]) {
    const obligatorio = campo === 'run' || campo === 'nombres' || campo === 'apellidos' || campo === 'fechaIngreso';
    setDatos((anteriores) => anteriores && { ...anteriores, [campo]: valor === '' && !obligatorio ? null : valor });
  }

  /** Revisa el RUN, guarda (alta o edición) y abre la ficha del usuario. */
  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!datos) return;
    if (!runValido(datos.run)) {
      setError('Revisa el RUN: el dígito verificador no corresponde.');
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      const destino = id ?? (await pacientesApi.registrar(datos)).id;
      if (id) await pacientesApi.editar(id, datos);
      navegar(`/usuarios/${destino}`);
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : 'No se pudo conectar con el servidor.');
    } finally {
      setEnviando(false);
    }
  }

  const texto = (campo: 'nombres' | 'apellidos' | 'alias' | 'direccion') => datos[campo] ?? '';

  return (
    <div className="space-y-4">
      <p className="text-slate-500">
        <Link to="/usuarios" className="font-medium text-prlac hover:underline">
          Usuarios
        </Link>{' '}
        › {id ? 'Editar usuario' : 'Nuevo usuario'}
      </p>

      <section className={`${CLASE_TARJETA} space-y-4`}>
        <h1 className="text-2xl font-medium text-tinta">{id ? 'Editar usuario' : 'Nuevo usuario'}</h1>
        <form onSubmit={enviar} className="grid gap-4 sm:grid-cols-2" noValidate>
          <label className="block">
            <span className={CLASE_ETIQUETA}>RUN</span>
            <input
              value={datos.run}
              onChange={(e) => cambiar('run', formatearRun(e.target.value))}
              placeholder="12.345.678-9"
              required
              className={CLASE_CAMPO}
            />
          </label>
          <label className="block">
            <span className={CLASE_ETIQUETA}>
              Alias <span className="font-normal text-slate-400">(nombre corto para los mensajes)</span>
            </span>
            <input
              value={texto('alias')}
              onChange={(e) => cambiar('alias', e.target.value)}
              placeholder="Sra. María"
              maxLength={60}
              className={CLASE_CAMPO}
            />
          </label>
          <label className="block">
            <span className={CLASE_ETIQUETA}>Nombres</span>
            <input value={texto('nombres')} onChange={(e) => cambiar('nombres', e.target.value)} required className={CLASE_CAMPO} />
          </label>
          <label className="block">
            <span className={CLASE_ETIQUETA}>Apellidos</span>
            <input
              value={texto('apellidos')}
              onChange={(e) => cambiar('apellidos', e.target.value)}
              required
              className={CLASE_CAMPO}
            />
          </label>
          <label className="block">
            <span className={CLASE_ETIQUETA}>Fecha de nacimiento</span>
            <input
              type="date"
              value={datos.fechaNacimiento ?? ''}
              max={hoyEnChile()}
              onChange={(e) => cambiar('fechaNacimiento', e.target.value)}
              className={CLASE_CAMPO}
            />
          </label>
          <label className="block">
            <span className={CLASE_ETIQUETA}>Sexo</span>
            <select
              value={datos.sexo ?? ''}
              onChange={(e) => cambiar('sexo', (e.target.value || null) as Sexo | null)}
              className={CLASE_CAMPO}
            >
              <option value="">No informado</option>
              <option value="FEMENINO">Femenino</option>
              <option value="MASCULINO">Masculino</option>
            </select>
          </label>
          <label className="block sm:col-span-2">
            <span className={CLASE_ETIQUETA}>Dirección</span>
            <input value={texto('direccion')} onChange={(e) => cambiar('direccion', e.target.value)} className={CLASE_CAMPO} />
          </label>
          <label className="block">
            <span className={CLASE_ETIQUETA}>Nivel de dependencia</span>
            <select
              value={datos.nivelDependencia ?? ''}
              onChange={(e) => cambiar('nivelDependencia', (e.target.value || null) as NivelDependencia | null)}
              className={CLASE_CAMPO}
            >
              <option value="">Por evaluar</option>
              <option value="MODERADA">Moderada</option>
              <option value="SEVERA">Severa</option>
            </select>
          </label>
          <label className="block">
            <span className={CLASE_ETIQUETA}>Fecha de ingreso al programa</span>
            <input
              type="date"
              value={datos.fechaIngreso}
              max={hoyEnChile()}
              onChange={(e) => cambiar('fechaIngreso', e.target.value)}
              required
              className={CLASE_CAMPO}
            />
          </label>
          <label className="block">
            <span className={CLASE_ETIQUETA}>Fecha del consentimiento informado</span>
            <input
              type="date"
              value={datos.fechaConsentimiento ?? ''}
              max={hoyEnChile()}
              onChange={(e) => cambiar('fechaConsentimiento', e.target.value)}
              className={CLASE_CAMPO}
            />
            <span className="mt-1 block text-xs text-slate-500">Sin consentimiento no se le puede entregar un reloj.</span>
          </label>
  
          {error && (
            <p role="alert" className={`${CLASE_ERROR} sm:col-span-2`}>
              {error}
            </p>
          )}
  
          <div className="flex gap-2 sm:col-span-2">
            <button
              type="submit"
              disabled={enviando || !datos.run || !datos.nombres.trim() || !datos.apellidos.trim() || !datos.fechaIngreso}
              className={CLASE_BOTON_PRINCIPAL}
            >
              {enviando ? 'Guardando…' : 'Guardar'}
            </button>
            <Link to={id ? `/usuarios/${id}` : '/usuarios'} className={CLASE_BOTON_SECUNDARIO}>
              Cancelar
            </Link>
          </div>
        </form>
      </section>
    </div>
  );
}
