import { useState } from 'react';
import { CLASE_TARJETA } from '../../comun/estilos';
import { useSesion } from '../sesion/SesionContext';
import { EnlaceGenerado } from './EnlaceGenerado';
import { FormularioCuenta } from './FormularioCuenta';
import type { RespuestaEnlace } from './tipos';

/**
 * Sección Administración. Por ahora, el alta de cuentas del equipo; aquí irán también la lista de
 * cuentas y la edición de permisos de los roles.
 */
export function PaginaAdministracion() {
  const { puede } = useSesion();
  const [creada, setCreada] = useState<{ enlace: RespuestaEnlace; nombre: string } | null>(null);

  return (
    <div className="space-y-6">
      <h1 className="text-xl font-semibold text-slate-900">Administración</h1>

      {puede('ADMINISTRACION:CREAR') && (
        <section className={`${CLASE_TARJETA} space-y-4`}>
          <div>
            <h2 className="text-lg font-semibold text-slate-900">Nueva cuenta del equipo</h2>
            <p className="text-sm text-slate-500">
              La persona recibe un enlace para elegir su propia contraseña. Nadie más la conoce.
            </p>
          </div>
          {creada ? (
            <EnlaceGenerado enlace={creada.enlace} nombre={creada.nombre} alTerminar={() => setCreada(null)} />
          ) : (
            <FormularioCuenta alCrear={(enlace, nombre) => setCreada({ enlace, nombre })} />
          )}
        </section>
      )}
    </div>
  );
}
