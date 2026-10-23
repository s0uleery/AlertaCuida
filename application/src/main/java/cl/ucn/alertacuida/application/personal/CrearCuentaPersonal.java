package cl.ucn.alertacuida.application.personal;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.error.CuentaDuplicada;
import cl.ucn.alertacuida.application.personal.modelo.DatosCuenta;
import cl.ucn.alertacuida.application.personal.modelo.EnlaceActivacion;
import cl.ucn.alertacuida.application.personal.modelo.NuevaCuenta;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import cl.ucn.alertacuida.domain.comun.Run;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * Alta de una persona del equipo (1.3.2). La cuenta queda inactiva y sin contraseña: la persona
 * elige la suya con el enlace de activación, así nadie más la conoce.
 */
public class CrearCuentaPersonal {

	private final CuentasPersonal cuentas;
	private final HashRun hashRun;
	private final EmisorEnlacesActivacion enlaces;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	public CrearCuentaPersonal(CuentasPersonal cuentas, HashRun hashRun, EmisorEnlacesActivacion enlaces,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.cuentas = cuentas;
		this.hashRun = hashRun;
		this.enlaces = enlaces;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * Valida los datos, guarda la cuenta y emite su enlace de activación, todo en una transacción.
	 *
	 * @param datos           lo que escribió el administrador
	 * @param idAdministrador quien crea la cuenta, para la auditoría
	 * @param ip              desde dónde llegó la petición, para la auditoría
	 * @return el enlace que el administrador le pasa a la persona
	 * @throws SolicitudRechazada si el RUN no es válido o el rol no existe
	 * @throws CuentaDuplicada    si ya hay una cuenta con ese RUN o correo
	 */
	public EnlaceActivacion crear(DatosCuenta datos, UUID idAdministrador, String ip) {
		NuevaCuenta cuenta = validar(datos);
		Instant ahora = reloj.instant();
		return transacciones.ejecutar(() -> {
			UUID id = cuentas.crear(cuenta);
			auditoria.registrar(idAdministrador, "CREAR", "USUARIO", id.toString(), ip, ahora);
			return enlaces.emitir(id, ahora);
		});
	}

	/** Revisa las reglas y normaliza: RUN sin puntos, correo en minúsculas, sin espacios sobrantes. */
	private NuevaCuenta validar(DatosCuenta datos) {
		Run run;
		try {
			run = Run.de(datos.run());
		} catch (IllegalArgumentException invalido) {
			throw new SolicitudRechazada(invalido.getMessage());
		}
		String runHash = hashRun.de(run);
		String correo = datos.correo().strip().toLowerCase(Locale.ROOT);
		if (!cuentas.rolActivo(datos.codigoRol())) {
			throw new SolicitudRechazada("El rol no existe o está desactivado");
		}
		if (cuentas.existeRunHash(runHash)) {
			throw new CuentaDuplicada("Ya existe una cuenta con ese RUN");
		}
		if (cuentas.existeCorreo(correo)) {
			throw new CuentaDuplicada("Ya existe una cuenta con ese correo");
		}
		String cargo = datos.cargo() == null || datos.cargo().isBlank() ? null : datos.cargo().strip();
		return new NuevaCuenta(run.valor(), runHash, datos.nombre().strip(), cargo, correo, datos.codigoRol());
	}

}
