import { Link } from 'react-router';
import { claseBoton } from '../../comun/ui/Boton';
import { PlantillaAcceso } from '../../navegacion/PlantillaAcceso';

/** Página pública /sesion-finalizada: se muestra al cerrar la sesión desde el menú del usuario. */
export function PaginaSesionFinalizada() {
  return (
    <PlantillaAcceso
      titulo="Sesión finalizada"
      subtitulo="Has cerrado tu sesión de forma segura en la plataforma AlertaCuida."
    >
      <Link to="/login" replace className={`${claseBoton('principal')} w-full`}>
        Volver a ingresar
      </Link>
    </PlantillaAcceso>
  );
}
