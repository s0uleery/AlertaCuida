import { useState } from 'react';
import { CLASE_BOTON_PRINCIPAL, CLASE_BOTON_SECUNDARIO, CLASE_CAMPO } from '../../comun/estilos';
import type { TokenReloj } from './tipos';

/**
 * Muestra el token del reloj recién emitido para ingresarlo en la app del reloj. No se vuelve a
 * mostrar: el backend solo guarda su hash. Si se pierde, se genera otro desde la ficha.
 */
export function TokenRelojMostrado({ token, alCerrar }: { token: TokenReloj; alCerrar: () => void }) {
  const [copiado, setCopiado] = useState(false);

  async function copiar() {
    try {
      await navigator.clipboard.writeText(token.token);
      setCopiado(true);
    } catch {
      // Sin permiso para el portapapeles: queda el campo para copiar a mano
    }
  }

  return (
    <div className="space-y-3 rounded-md border border-emerald-200 bg-emerald-50 p-4">
      <p className="font-medium text-emerald-900">Token del reloj</p>
      <p className="text-sm text-emerald-900">
        Ingrésalo en la app del reloj para que pueda enviar datos. <strong>No se vuelve a mostrar</strong>; si se
        pierde, genera uno nuevo.
      </p>
      <input
        readOnly
        value={token.token}
        onFocus={(e) => e.target.select()}
        aria-label="Token del reloj"
        className={`${CLASE_CAMPO} bg-white font-mono text-sm`}
      />
      <div className="flex gap-2">
        <button onClick={copiar} className={CLASE_BOTON_PRINCIPAL}>
          {copiado ? 'Copiado ✓' : 'Copiar token'}
        </button>
        <button onClick={alCerrar} className={CLASE_BOTON_SECUNDARIO}>
          Listo, ya lo ingresé
        </button>
      </div>
    </div>
  );
}
