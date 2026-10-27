import { useState } from 'react';
import { Link } from 'react-router';
import { CLASE_TARJETA } from '../../comun/estilos';
import { EnlaceGenerado } from './EnlaceGenerado';
import { FormularioCuenta } from './FormularioCuenta';
import type { RespuestaEnlace } from './tipos';

/** Alta de una cuenta del equipo, a la que se llega con "Agregar perfil". */
export function NuevaCuenta() {
  const [creada, setCreada] = useState<{ enlace: RespuestaEnlace; nombre: string } | null>(null);

  return (
    <div className="space-y-4">
      <p className="text-slate-500">
        <Link to="/administracion" className="font-medium text-prlac hover:underline">
          Administración
        </Link>{' '}
        › Agregar perfil
      </p>

      <section className={`${CLASE_TARJETA} space-y-4`}>
        <div>
          <h1 className="text-2xl font-medium text-tinta">Nueva cuenta del equipo</h1>
          <p className="text-tinta">
            La persona recibe un enlace para elegir su propia contraseña. Nadie más la conoce.
          </p>
        </div>
        {creada ? (
          <EnlaceGenerado enlace={creada.enlace} nombre={creada.nombre} alTerminar={() => setCreada(null)} />
        ) : (
          <FormularioCuenta alCrear={(enlace, nombre) => setCreada({ enlace, nombre })} />
        )}
      </section>
    </div>
  );
}
