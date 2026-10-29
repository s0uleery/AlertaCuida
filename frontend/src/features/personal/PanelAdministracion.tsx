import { useSearchParams } from 'react-router';
import { CLASE_TARJETA } from '../../comun/estilos';
import { Auditoria } from './Auditoria';
import { PerfilesEquipo } from './PerfilesEquipo';
import { PermisosRoles } from './PermisosRoles';

const PESTANAS = [
  ['perfiles', 'Perfiles'],
  ['permisos', 'Permisos'],
  ['auditoria', 'Auditoría'],
] as const;

type Pestana = (typeof PESTANAS)[number][0];

/**
 * Panel de Administración con sus tres pestañas, como en el mockup del PRLAC. La pestaña va en la
 * dirección (?pestana=auditoria) para que el botón "atrás" y los enlaces vuelvan a la misma.
 */
export function PanelAdministracion() {
  const [parametros, setParametros] = useSearchParams();
  const elegida = parametros.get('pestana');
  const pestana: Pestana = PESTANAS.some(([valor]) => valor === elegida) ? (elegida as Pestana) : 'perfiles';

  return (
    <section className={CLASE_TARJETA}>
      <h1 className="text-2xl font-medium text-tinta">Administración</h1>
      <p className="mb-4 text-tinta">
        Gestiona los perfiles del equipo, sus permisos y revisa los movimientos realizados en la plataforma.
      </p>

      <div className="mb-4 flex border-b border-slate-300" role="tablist">
        {PESTANAS.map(([valor, etiqueta]) => (
          <button
            key={valor}
            role="tab"
            aria-selected={pestana === valor}
            onClick={() => setParametros(valor === 'perfiles' ? {} : { pestana: valor })}
            className={`-mb-px border-b-3 px-4 py-2.5 font-medium ${
              pestana === valor ? 'border-prlac text-prlac' : 'border-transparent text-slate-500 hover:text-slate-700'
            }`}
          >
            {etiqueta}
          </button>
        ))}
      </div>

      {pestana === 'perfiles' && <PerfilesEquipo />}
      {pestana === 'permisos' && <PermisosRoles />}
      {pestana === 'auditoria' && <Auditoria />}
    </section>
  );
}
