package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.modelo.CuentaEquipo;
import cl.ucn.alertacuida.application.personal.puerto.EquipoPersonal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cambia el celular con WhatsApp y el opt-in de una cuenta ya creada; antes solo se pedían en el alta y al pasar
 * a coordinador (decisión del 19-11-2026: lo hace ADMINISTRACION:EDITAR). Un coordinador no puede quedar sin
 * teléfono, porque recibe los avisos de la oficina PRLAC.
 */
public class CambiarTelefonoPersonal {

	private final EquipoPersonal equipo;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public CambiarTelefonoPersonal(EquipoPersonal equipo, RegistroAuditoria auditoria, Transacciones transacciones,
			Clock reloj) {
		this.equipo = equipo;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Guarda el teléfono nuevo, o lo quita si viene vacío. Aceptar WhatsApp deja el opt-in con la hora del cambio;
	 * no aceptarlo lo deja vacío. La auditoría no guarda el número.
	 *
	 * @param telefono       el celular; vacío para quitarlo (no en un coordinador)
	 * @param aceptaWhatsapp si la persona aceptó recibir WhatsApp
	 * @param idQuien        quien hace el cambio, para la auditoría
	 * @throws NoEncontrado       si la cuenta no existe
	 * @throws SolicitudRechazada si el número no es un celular, un coordinador queda sin teléfono o se acepta
	 *                            WhatsApp sin teléfono
	 */
	public void cambiar(UUID idCuenta, String telefono, boolean aceptaWhatsapp, UUID idQuien, String ip) {
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			CuentaEquipo cuenta = equipo.buscar(idCuenta)
					.orElseThrow(() -> new NoEncontrado("No existe esa cuenta del equipo"));
			String normalizado = TelefonoAvisos.validar(cuenta.codigoRol(), telefono, aceptaWhatsapp, false);
			equipo.guardarTelefono(idCuenta, normalizado, aceptaWhatsapp ? ahora : null);
			Map<String, Object> detalle = new LinkedHashMap<>();
			detalle.put("telefono", normalizado == null ? "quitado" : "cambiado");
			detalle.put("aceptaWhatsapp", aceptaWhatsapp);
			auditoria.registrar(idQuien, "MODIFICAR", "USUARIO", idCuenta.toString(), ip, ahora, detalle);
			return null;
		});
	}

}
