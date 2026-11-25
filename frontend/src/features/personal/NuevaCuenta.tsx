import { useState } from 'react';
import { EncabezadoVista, Panel } from '../../comun/ui/Panel';
import { EnlaceGenerado } from './EnlaceGenerado';
import { FormularioCuenta } from './FormularioCuenta';
import type { RespuestaEnlace } from './tipos';

/** Alta de una cuenta del equipo, a la que se llega con "Agregar perfil". */
export function NuevaCuenta() {
  const [creada, setCreada] = useState<{ enlace: RespuestaEnlace; nombre: string } | null>(null);

  return (
    <>
      <EncabezadoVista
        migas={[
          { texto: 'Administración', a: '/administracion' },
          { texto: 'Agregar perfil', a: '/administracion/nuevo' },
        ]}
        titulo="Nueva cuenta del equipo"
        subtitulo="La persona recibe un enlace para elegir su propia contraseña. Nadie más la conoce."
      />
      <Panel className="max-w-3xl">
        {creada ? (
          <EnlaceGenerado enlace={creada.enlace} nombre={creada.nombre} alTerminar={() => setCreada(null)} />
        ) : (
          <FormularioCuenta alCrear={(enlace, nombre) => setCreada({ enlace, nombre })} />
        )}
      </Panel>
    </>
  );
}
