import { useState, type FormEvent } from 'react';
import { CLASE_CAMPO, CLASE_ETIQUETA } from '../../comun/estilos';
import { Aviso } from '../../comun/ui/Aviso';
import { Boton } from '../../comun/ui/Boton';
import { Dialogo } from '../../comun/ui/Dialogo';

/** Para qué se pide el teléfono. */
export type MotivoTelefono = 'COORDINADOR' | 'EDITAR';

/** Propiedades de DialogoTelefono. */
interface Props {
  /** Nombre de la persona */
  nombre: string;
  /**
   * COORDINADOR: pasa a Coordinador una cuenta sin teléfono (el número es obligatorio).
   * EDITAR: cambia o quita el celular de la cuenta (vacío = quitarlo, salvo a un coordinador).
   */
  motivo: MotivoTelefono;
  /** Mensaje del backend si el último intento falló */
  error: string | null;
  guardando: boolean;
  /** Se llama con el teléfono escrito (puede ir vacío al editar) y si la persona aceptó WhatsApp */
  alGuardar: (telefono: string, aceptaWhatsapp: boolean) => void;
  alCancelar: () => void;
}

/**
 * Pide el celular con WhatsApp de una cuenta del equipo: al pasarla a COORDINADOR (los coordinadores reciben los
 * avisos de la oficina PRLAC) o al editarlo desde la tabla de perfiles. El número lo valida el backend y su
 * mensaje se muestra tal cual.
 */
export function DialogoTelefono({ nombre, motivo, error, guardando, alGuardar, alCancelar }: Props) {
  const [telefono, setTelefono] = useState('');
  const [aceptaWhatsapp, setAceptaWhatsapp] = useState(false);
  const obligatorio = motivo === 'COORDINADOR';

  /** Entrega el teléfono a la tabla, que hace el cambio. */
  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (telefono.trim() || !obligatorio) alGuardar(telefono.trim(), aceptaWhatsapp && !!telefono.trim());
  }

  return (
    <Dialogo
      titulo={`Teléfono de ${nombre}`}
      alCerrar={alCancelar}
      ocupado={guardando}
      ancho="max-w-md"
      pie={
        <>
          <Boton variante="contorno" onClick={alCancelar} disabled={guardando}>
            Cancelar
          </Boton>
          <Boton
            type="submit"
            form="formulario-telefono"
            variante="principal"
            disabled={guardando || (obligatorio && !telefono.trim())}
          >
            {guardando ? 'Guardando…' : obligatorio ? 'Cambiar a Coordinador' : 'Guardar teléfono'}
          </Boton>
        </>
      }
    >
      <form id="formulario-telefono" onSubmit={enviar} className="space-y-4" noValidate>
        <p className="text-[13px] text-apagado">
          {obligatorio
            ? `Los coordinadores reciben por WhatsApp los avisos de la oficina PRLAC en horario laboral. Para pasar a ${nombre} a Coordinador falta su celular.`
            : 'Escribe el celular nuevo. Si lo dejas vacío, se quita el celular de la cuenta (un coordinador no puede quedar sin él).'}
        </p>

        <label className="block">
          <span className={CLASE_ETIQUETA}>Teléfono con WhatsApp</span>
          <input
            type="tel"
            value={telefono}
            onChange={(e) => {
              setTelefono(e.target.value);
              // Sin teléfono no hay a dónde enviar WhatsApp
              if (!e.target.value.trim()) setAceptaWhatsapp(false);
            }}
            placeholder="+56 9 1234 5678"
            maxLength={30}
            required={obligatorio}
            autoFocus
            className={`${CLASE_CAMPO} cifra`}
          />
        </label>

        <label className="flex items-start gap-2 text-[13px]">
          <input
            type="checkbox"
            checked={aceptaWhatsapp}
            disabled={!telefono.trim()}
            onChange={(e) => setAceptaWhatsapp(e.target.checked)}
            className="mt-1 accent-teal"
          />
          <span>
            La persona aceptó recibir avisos por WhatsApp
            <span className="block text-xs text-apagado">Sin esta aceptación, Meta no permite enviarle mensajes.</span>
          </span>
        </label>

        {error && <Aviso tono="error">{error}</Aviso>}
      </form>
    </Dialogo>
  );
}
