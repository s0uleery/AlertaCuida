/**
 * Identidad institucional del mockup (Manual de Marca PRLAC): franja del Gobierno de Chile y la tríada de marcas
 * PRLAC · Chile Cuida · Ministerio de Desarrollo Social y Familia, en ese orden. Son reproducciones en texto y SVG;
 * si llegan los logos oficiales en imagen, se reemplazan aquí.
 */

/** Franja bicolor del Gobierno de Chile: 32 % azul y 68 % rojo. */
export function FranjaGobierno() {
  return (
    <div className="flex h-1" aria-hidden="true">
      <span className="w-[32%] bg-gob-azul" />
      <span className="w-[68%] bg-gob-rojo" />
    </div>
  );
}

/** 1° Programa Red Local de Apoyos y Cuidados. */
function LogoPrlac({ claro }: { claro: boolean }) {
  const color = claro ? 'text-white' : 'text-navy';
  return (
    <div className={`leading-[1.04] ${color}`}>
      <small className="mb-px block text-[8.5px] font-medium tracking-[0.06em] uppercase">Programa</small>
      <b className="block text-[13.5px] font-black tracking-[-0.02em]">
        Red Local
        <br />
        de Apoyos
        <br />y Cuidados
      </b>
    </div>
  );
}

/** 2° Chile Cuida, Sistema Nacional de Apoyos y Cuidados. */
function LogoChileCuida({ claro }: { claro: boolean }) {
  return (
    <div className="flex items-center gap-2">
      <svg width="28" height="28" viewBox="0 0 32 32" fill="none" aria-hidden="true">
        <circle cx="16" cy="4.5" r="2.4" fill="#0051a8" />
        <circle cx="26.5" cy="11.5" r="2.4" fill="#0051a8" />
        <circle cx="22.5" cy="24.5" r="2.4" fill="#0051a8" />
        <circle cx="9.5" cy="24.5" r="2.4" fill="#d62828" />
        <circle cx="5.5" cy="11.5" r="2.4" fill="#0051a8" />
        <path d="M10.5 7.5A10 10 0 0 1 21.5 7.5" stroke="#0051a8" strokeWidth="2.2" strokeLinecap="round" />
        <path d="M25.5 15A10 10 0 0 1 23 21.5" stroke="#0051a8" strokeWidth="2.2" strokeLinecap="round" />
        <path d="M18.5 25.5A10 10 0 0 1 11.5 23" stroke="#d62828" strokeWidth="2.2" strokeLinecap="round" />
        <path d="M7 18.5A10 10 0 0 1 8.5 11.5" stroke="#0051a8" strokeWidth="2.2" strokeLinecap="round" />
        <path
          d="M16 10.2l1.6 3.4 3.7.5-2.7 2.6.6 3.7-3.2-1.7-3.2 1.7.6-3.7-2.7-2.6 3.7-.5L16 10.2z"
          fill={claro ? '#ffffff' : '#003349'}
        />
      </svg>
      <div>
        <b className={`block text-sm leading-[0.98] font-black tracking-[-0.02em] ${claro ? 'text-white' : 'text-gob-azul'}`}>
          Chile
          <br />
          Cuida
        </b>
        <small className={`mt-[3px] block text-[8px] font-medium whitespace-nowrap ${claro ? 'text-white/85' : 'text-navy'}`}>
          Sistema Nacional de Apoyos y Cuidados
        </small>
      </div>
    </div>
  );
}

/** 3° Ministerio de Desarrollo Social y Familia, Gobierno de Chile. */
function LogoMdsf() {
  return (
    <div className="flex h-[38px] flex-none items-stretch overflow-hidden rounded-[2px] border border-black/10">
      <div className="grid w-[26px] place-items-center bg-gob-azul p-[3px]">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path d="M12 2l2.9 6.1 6.7.9-4.8 4.7 1.1 6.7L12 17.3l-5.9 3.1 1.1-6.7-4.8-4.7 6.7-.9L12 2z" fill="#ffffff" />
        </svg>
      </div>
      <div className="flex flex-col justify-between bg-gob-rojo px-2 py-1 text-[7.5px] leading-[1.06] font-bold text-white">
        <div>
          Ministerio de
          <br />
          Desarrollo Social
          <br />y Familia
        </div>
        <span className="text-[6.5px] font-normal opacity-95">Gobierno de Chile</span>
      </div>
    </div>
  );
}

/** Separador vertical entre logos. */
function Divisor({ claro, ocultarEnCelular = false }: { claro: boolean; ocultarEnCelular?: boolean }) {
  return (
    <div
      aria-hidden="true"
      className={`h-[34px] w-px flex-none ${claro ? 'bg-white/30' : 'bg-linea-oscura'} ${ocultarEnCelular ? 'max-[760px]:hidden' : ''}`}
    />
  );
}

/**
 * Las tres marcas juntas. En pantallas angostas (menos de 760 px) se oculta la del Ministerio, como en el mockup.
 *
 * @param claro versión para fondo navy (pantallas de acceso)
 */
export function TriadaMarcas({ claro = false }: { claro?: boolean }) {
  return (
    <div
      className="inline-flex items-center gap-4"
      role="img"
      aria-label="Programa Red Local de Apoyos y Cuidados · Chile Cuida · Ministerio de Desarrollo Social y Familia"
    >
      <LogoPrlac claro={claro} />
      <Divisor claro={claro} />
      <LogoChileCuida claro={claro} />
      <Divisor claro={claro} ocultarEnCelular />
      <div className="max-[760px]:hidden">
        <LogoMdsf />
      </div>
    </div>
  );
}
