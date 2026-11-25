import type { ReactNode } from 'react';
import { FranjaGobierno, TriadaMarcas } from './Marcas';

/**
 * Caja centrada de las páginas sin sesión (ingreso, recuperar, activar, sesión finalizada y la respuesta del
 * contacto): franja del gobierno, cabecera navy con las marcas y el contenido debajo, como en el mockup.
 *
 * @param ancho clase de ancho máximo de Tailwind; por defecto 440 px
 */
export function PlantillaAcceso({
  titulo,
  subtitulo,
  children,
  ancho = 'max-w-[440px]',
}: {
  titulo: ReactNode;
  subtitulo?: ReactNode;
  children: ReactNode;
  ancho?: string;
}) {
  return (
    <main className="min-h-screen bg-fondo px-4 pt-16 pb-10 max-[520px]:pt-8">
      <div className={`mx-auto w-full ${ancho} overflow-hidden rounded-lg border border-linea-oscura bg-superficie shadow-menu`}>
        <FranjaGobierno />
        <div className="bg-navy px-6 py-4">
          <TriadaMarcas claro />
        </div>
        <div className="space-y-5 p-7 max-[520px]:p-5">
          <div>
            <h1 className="text-xl font-bold text-navy">{titulo}</h1>
            {subtitulo && <p className="mt-1 text-[13px] text-apagado">{subtitulo}</p>}
          </div>
          {children}
        </div>
      </div>
      <p className="mt-5 text-center text-[11.5px] text-apagado">AlertaCuida · Monitoreo y teleasistencia · Comuna de Coquimbo</p>
    </main>
  );
}
