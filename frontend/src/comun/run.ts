// Mismas reglas que Run.java: sin puntos, con guion, K mayúscula y dígito verificador módulo 11.

export function limpiarRun(texto: string): string {
  return texto.replace(/[^0-9kK]/g, '').toUpperCase();
}

/** Dígito verificador módulo 11 del cuerpo del RUN (sin el dígito): '0' a '9' o 'K'. */
export function digitoVerificador(cuerpo: string): string {
  let suma = 0;
  let factor = 2;
  for (let i = cuerpo.length - 1; i >= 0; i--) {
    suma += Number(cuerpo[i]) * factor;
    factor = factor === 7 ? 2 : factor + 1;
  }
  const resto = 11 - (suma % 11);
  if (resto === 11) return '0';
  if (resto === 10) return 'K';
  return String(resto);
}

/** true si el RUN tiene formato válido y su dígito verificador corresponde. */
export function runValido(texto: string): boolean {
  const limpio = limpiarRun(texto);
  if (!/^\d{7,8}[0-9K]$/.test(limpio)) return false;
  return digitoVerificador(limpio.slice(0, -1)) === limpio.slice(-1);
}

/** Formato de lectura mientras se escribe: 12.345.678-5 */
export function formatearRun(texto: string): string {
  const limpio = limpiarRun(texto).slice(0, 9);
  if (limpio.length < 2) return limpio;
  const cuerpo = limpio.slice(0, -1).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${cuerpo}-${limpio.slice(-1)}`;
}
