import { limpiarRun } from '../../comun/run';
import type { ResumenPaciente } from './tipos';

/** true si el paciente coincide con lo buscado: por nombre, alias o RUN (con o sin puntos). */
export function coincide(fila: ResumenPaciente, busqueda: string): boolean {
  const d = fila.paciente.datos;
  const texto = `${d.nombres} ${d.apellidos} ${d.alias ?? ''}`.toLowerCase();
  const runBuscado = limpiarRun(busqueda);
  return texto.includes(busqueda.toLowerCase()) || (runBuscado.length > 2 && limpiarRun(d.run).includes(runBuscado));
}
