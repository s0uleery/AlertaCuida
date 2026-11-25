import { useState } from 'react';
import { ErrorApi } from '../../api/cliente';
import { Boton } from '../../comun/ui/Boton';
import { useToast } from '../../comun/ui/Toast';
import { useSesion } from '../sesion/SesionContext';
import type { Formato } from './reportesApi';

/**
 * Botones "Exportar Excel" y "Exportar PDF" del mockup. Solo aparecen con REPORTES:EXPORTAR; cada descarga queda
 * en la auditoría del servidor.
 *
 * @param exportar descarga el archivo en el formato pedido
 */
export function BotonesExportar({ exportar }: { exportar: (formato: Formato) => Promise<void> }) {
  const { puede } = useSesion();
  const avisar = useToast();
  const [enCurso, setEnCurso] = useState<Formato | null>(null);

  if (!puede('REPORTES:EXPORTAR')) return null;

  /** Descarga y avisa si falla. */
  async function descargar(formato: Formato) {
    setEnCurso(formato);
    try {
      await exportar(formato);
      avisar(formato === 'xlsx' ? 'Planilla Excel descargada' : 'PDF descargado');
    } catch (e) {
      avisar(e instanceof ErrorApi ? e.message : 'No se pudo descargar el archivo.');
    } finally {
      setEnCurso(null);
    }
  }

  return (
    <div className="flex flex-wrap gap-2">
      <Boton variante="contorno" chico disabled={enCurso !== null} onClick={() => descargar('xlsx')}>
        {enCurso === 'xlsx' ? 'Descargando…' : 'Exportar Excel'}
      </Boton>
      <Boton variante="contorno" chico disabled={enCurso !== null} onClick={() => descargar('pdf')}>
        {enCurso === 'pdf' ? 'Descargando…' : 'Exportar PDF'}
      </Boton>
    </div>
  );
}
