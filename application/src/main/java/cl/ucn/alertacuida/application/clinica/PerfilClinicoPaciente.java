package cl.ucn.alertacuida.application.clinica;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.ConfiguracionGuardada;
import cl.ucn.alertacuida.application.clinica.modelo.PerfilClinico;
import cl.ucn.alertacuida.application.clinica.modelo.UmbralGuardado;
import cl.ucn.alertacuida.application.clinica.modelo.UmbralVigente;
import cl.ucn.alertacuida.application.clinica.modelo.ValoresBase;
import cl.ucn.alertacuida.application.clinica.modelo.ZonaGuardada;
import cl.ucn.alertacuida.application.clinica.puerto.PerfilesClinicos;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.clinica.ZonaSegura;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Perfil clínico de un paciente: módulos que vigila el reloj, GPS, modo conservador, minutos de
 * espera entre contactos, umbrales de FC y SpO2 y zona segura. Cada cambio agrega una fila al historial y queda
 * en la auditoría (quién y cuándo); las filas anteriores no se modifican.
 *
 * <p>Donde el paciente no tiene ajustes propios rigen los {@link ValoresBase}.
 */
public class PerfilClinicoPaciente {

	/** Máximo de minutos de espera antes de pasar al siguiente contacto. */
	static final int ESPERA_MAXIMA = 60;
	/** Largo máximo del motivo de un cambio. */
	static final int LARGO_MAXIMO_MOTIVO = 500;

	private final PerfilesClinicos perfiles;
	private final Pacientes pacientes;
	private final ValoresBase base;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionPerfilYContactos. */
	public PerfilClinicoPaciente(PerfilesClinicos perfiles, Pacientes pacientes, ValoresBase base,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		this.perfiles = perfiles;
		this.pacientes = pacientes;
		this.base = base;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.reloj = reloj;
	}

	/**
	 * @return lo que rige hoy, con los valores base donde no hay ajustes propios
	 * @throws NoEncontrado si el paciente no existe
	 */
	public PerfilClinico consultar(UUID idPaciente) {
		exigirPaciente(idPaciente);
		Optional<ConfiguracionGuardada> configuracion = perfiles.configuracionVigente(idPaciente);
		AjustesClinicos ajustes = configuracion.map(ConfiguracionGuardada::ajustes).orElse(AjustesClinicos.POR_DEFECTO);
		int espera = ajustes.minutosEspera() != null ? ajustes.minutosEspera() : base.minutosEspera();
		return new PerfilClinico(ajustes, configuracion.map(ConfiguracionGuardada::vigenteDesde).orElse(null), espera,
				umbralVigente(idPaciente, TipoLectura.FC), umbralVigente(idPaciente, TipoLectura.SPO2),
				perfiles.zonaVigente(idPaciente).orElse(null));
	}

	/**
	 * Cambia los módulos y opciones. Si no cambia nada (salvo el motivo) no agrega una fila: así
	 * no se crea una versión de configuración nueva para el reloj.
	 *
	 * @param ajustes   los nuevos ajustes completos
	 * @param idUsuario quien los cambia, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @return si se guardó un cambio
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si los minutos de espera o el motivo no sirven, o si se activa la
	 *                            zona segura sin GPS o sin una zona guardada
	 */
	public boolean cambiarAjustes(UUID idPaciente, AjustesClinicos ajustes, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		if (ajustes == null || ajustes.modulos() == null) {
			throw new SolicitudRechazada("Faltan los módulos");
		}
		if (ajustes.modulos().zonaSegura()) {
			if (!ajustes.gpsActivo()) {
				throw new SolicitudRechazada("La zona segura necesita el GPS activo");
			}
			if (perfiles.zonaVigente(idPaciente).isEmpty()) {
				throw new SolicitudRechazada("Primero define la zona segura: centro y radio");
			}
		}
		Integer espera = ajustes.minutosEspera();
		if (espera != null && (espera < 1 || espera > ESPERA_MAXIMA)) {
			throw new SolicitudRechazada("Los minutos de espera deben estar entre 1 y " + ESPERA_MAXIMA);
		}
		String motivo = ajustes.motivo() == null || ajustes.motivo().isBlank() ? null : ajustes.motivo().strip();
		if (motivo != null && motivo.length() > LARGO_MAXIMO_MOTIVO) {
			throw new SolicitudRechazada("El motivo no puede tener más de " + LARGO_MAXIMO_MOTIVO + " caracteres");
		}
		AjustesClinicos nuevos = new AjustesClinicos(ajustes.modulos(), ajustes.gpsActivo(),
				ajustes.modoConservador(), espera, motivo);
		AjustesClinicos actuales = perfiles.configuracionVigente(idPaciente).map(ConfiguracionGuardada::ajustes)
				.orElse(AjustesClinicos.POR_DEFECTO);
		if (nuevos.igualesA(actuales)) {
			return false;
		}
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			perfiles.guardarConfiguracion(idPaciente, ahora, nuevos);
			auditoria.registrar(idUsuario, "CREAR", "CONFIGURACION", idPaciente + "|" + ahora, ip, ahora);
			return null;
		});
		return true;
	}

	/**
	 * Ajusta el umbral de una variable para el paciente.
	 *
	 * @param variable  FC o SPO2
	 * @param graveBajo bajo este valor la lectura es grave
	 * @param normalMin desde este valor la lectura es normal
	 * @param normalMax hasta este valor la lectura es normal (vacío en SpO2)
	 * @param graveAlto sobre este valor la lectura es grave (vacío en SpO2)
	 * @param idUsuario quien lo ajusta, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @return si se guardó un cambio (no se guarda si es igual al vigente)
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si los límites no van en orden, falta o sobra alguno
	 */
	public boolean ajustarUmbral(UUID idPaciente, TipoLectura variable, BigDecimal graveBajo, BigDecimal normalMin,
			BigDecimal normalMax, BigDecimal graveAlto, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		if (variable == null) {
			throw new SolicitudRechazada("Indica si el umbral es de FC o de SpO2");
		}
		Umbral umbral;
		try {
			umbral = Umbral.de(variable, graveBajo, normalMin, normalMax, graveAlto);
		} catch (IllegalArgumentException e) {
			throw new SolicitudRechazada(e.getMessage());
		}
		return guardarUmbral(idPaciente, variable, umbral, idUsuario, ip);
	}

	/**
	 * Vuelve al umbral base sin borrar el historial: agrega una fila vacía.
	 *
	 * @return si se guardó un cambio (no se guarda si ya regía el base)
	 * @throws NoEncontrado si el paciente no existe
	 */
	public boolean volverAlUmbralBase(UUID idPaciente, TipoLectura variable, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		return guardarUmbral(idPaciente, variable, null, idUsuario, ip);
	}

	/**
	 * Define o cambia la zona segura del paciente. No la activa: eso se hace en los ajustes, con el
	 * módulo zonaSegura, para que se pueda dejar guardada y apagada.
	 *
	 * @param latitud     del centro, en grados
	 * @param longitud    del centro, en grados
	 * @param radioMetros distancia desde el centro que se considera dentro de la zona
	 * @param severidad   gravedad de la alerta al salir; vacía = media
	 * @param idUsuario   quien la cambia, para la auditoría
	 * @param ip          desde dónde llegó la petición, para la auditoría
	 * @return si se guardó un cambio (no se guarda si es igual a la vigente)
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si el centro está fuera del mapa o el radio fuera de rango
	 */
	public boolean definirZona(UUID idPaciente, BigDecimal latitud, BigDecimal longitud, Integer radioMetros,
			Severidad severidad, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		ZonaSegura zona;
		try {
			zona = ZonaSegura.de(latitud, longitud, radioMetros, severidad);
		} catch (IllegalArgumentException e) {
			throw new SolicitudRechazada(e.getMessage());
		}
		ZonaSegura actual = perfiles.zonaVigente(idPaciente).map(ZonaGuardada::zona).orElse(null);
		if (zona.equals(actual)) {
			return false;
		}
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			perfiles.guardarZona(idPaciente, ahora, zona);
			auditoria.registrar(idUsuario, "CREAR", "ZONA_SEGURA", idPaciente + "|" + ahora, ip, ahora);
			return null;
		});
		return true;
	}

	/** Agrega la fila del umbral si cambia algo, con su auditoría. */
	private boolean guardarUmbral(UUID idPaciente, TipoLectura variable, Umbral umbral, UUID idUsuario, String ip) {
		Umbral actual = perfiles.umbralVigente(idPaciente, variable).map(UmbralGuardado::umbral).orElse(null);
		if (Objects.equals(actual, umbral)) {
			return false;
		}
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			perfiles.guardarUmbral(idPaciente, variable, ahora, umbral);
			auditoria.registrar(idUsuario, "CREAR", "UMBRAL", idPaciente + "|" + variable + "|" + ahora, ip, ahora);
			return null;
		});
		return true;
	}

	/** @return el umbral propio vigente, o el base si no hay o se volvió al base */
	private UmbralVigente umbralVigente(UUID idPaciente, TipoLectura variable) {
		Optional<UmbralGuardado> guardado = perfiles.umbralVigente(idPaciente, variable);
		if (guardado.isPresent() && guardado.get().umbral() != null) {
			return new UmbralVigente(guardado.get().umbral(), true, guardado.get().vigenteDesde());
		}
		return new UmbralVigente(base.umbral(variable), false, guardado.map(UmbralGuardado::vigenteDesde).orElse(null));
	}

	/** @throws NoEncontrado si el paciente no existe */
	private void exigirPaciente(UUID idPaciente) {
		pacientes.buscar(idPaciente).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
	}

}
