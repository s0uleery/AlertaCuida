import { Link, useSearchParams } from 'react-router';
import { claseBoton } from '../../comun/ui/Boton';
import { EncabezadoVista } from '../../comun/ui/Panel';
import { Pestanas } from '../../comun/ui/Pestanas';
import { useSesion } from '../sesion/SesionContext';
import { Auditoria } from './Auditoria';
import { PerfilesEquipo } from './PerfilesEquipo';
import { PermisosRoles } from './PermisosRoles';

const PESTANAS = [
  { clave: 'perfiles', texto: 'Equipo y roles' },
  { clave: 'permisos', texto: 'Matriz de permisos' },
  { clave: 'auditoria', texto: 'Auditoría' },
] as const;

type PestanaAdministracion = (typeof PESTANAS)[number]['clave'];

/**
 * Panel de Administración con sus tres pestañas, como en el mockup del PRLAC. La pestaña va en la dirección
 * (?pestana=auditoria) para que el botón "atrás" y los enlaces vuelvan a la misma.
 */
export function PanelAdministracion() {
  const { puede } = useSesion();
  const [parametros, setParametros] = useSearchParams();
  const elegida = parametros.get('pestana');
  const pestana: PestanaAdministracion = PESTANAS.some((p) => p.clave === elegida)
    ? (elegida as PestanaAdministracion)
    : 'perfiles';

  return (
    <>
      <EncabezadoVista
        titulo="Administración"
        subtitulo="Perfiles del equipo, sus permisos y los movimientos realizados en la plataforma."
        acciones={
          puede('ADMINISTRACION:CREAR') && (
            <Link to="/administracion/nuevo" className={claseBoton('principal')}>
              + Agregar perfil
            </Link>
          )
        }
      />
      <Pestanas
        etiqueta="Secciones de administración"
        pestanas={[...PESTANAS]}
        activa={pestana}
        alCambiar={(valor) => setParametros(valor === 'perfiles' ? {} : { pestana: valor })}
      />
      {pestana === 'perfiles' && <PerfilesEquipo />}
      {pestana === 'permisos' && <PermisosRoles />}
      {pestana === 'auditoria' && <Auditoria />}
    </>
  );
}
