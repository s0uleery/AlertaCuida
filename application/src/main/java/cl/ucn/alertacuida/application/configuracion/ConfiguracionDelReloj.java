package cl.ucn.alertacuida.application.configuracion;

import cl.ucn.alertacuida.application.clinica.PerfilClinicoPaciente;
import cl.ucn.alertacuida.application.clinica.modelo.PerfilClinico;
import cl.ucn.alertacuida.application.configuracion.modelo.ConfiguracionReloj;
import cl.ucn.alertacuida.application.configuracion.modelo.ContactoConfigurado;
import cl.ucn.alertacuida.application.configuracion.modelo.ContenidoConfiguracion;
import cl.ucn.alertacuida.application.contacto.ContactosPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import cl.ucn.alertacuida.domain.calendario.Feriado;
import cl.ucn.alertacuida.domain.clinica.ZonaSegura;
import cl.ucn.alertacuida.domain.comun.ZonaHoraria;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Arma la configuración que el reloj descarga: perfil clínico (con los valores base donde el
 * paciente no tiene ajustes), zona segura, contactos sin número ni correo, plazo de espera y
 * calendario hábil.
 *
 * <p>No se guarda: se calcula cada vez de lo vigente, y su versión es el hash del contenido. Así un
 * cambio en la base, en application.properties o en calendario.yml cambia la versión sin que nadie
 * tenga que acordarse de "publicar" una configuración nueva.
 */
public class ConfiguracionDelReloj implements VersionVigente {

	/** El reloj recorre la lista de contactos dos veces antes de informar SIN_RESPUESTA. */
	static final int RONDAS = 2;

	private final PerfilClinicoPaciente perfiles;
	private final ContactosPaciente contactos;
	private final CalendarioHabil calendario;
	private final String versionMatriz;
	private final int minutosTendencia;
	private final Severidad severidadTendencia;
	private final Clock reloj;

	/**
	 * Recibe lo que usa; lo arma ConfiguracionApiReloj.
	 *
	 * @param versionMatriz      versión de la matriz FC × SpO2 (alertacuida.clinica.version-matriz)
	 * @param minutosTendencia   tiempo de la regla de tendencia (alertacuida.clinica.minutos-tendencia)
	 * @param severidadTendencia gravedad de esa alerta (alertacuida.clinica.severidad-tendencia)
	 */
	public ConfiguracionDelReloj(PerfilClinicoPaciente perfiles, ContactosPaciente contactos,
			CalendarioHabil calendario, String versionMatriz, int minutosTendencia, Severidad severidadTendencia,
			Clock reloj) {
		this.perfiles = perfiles;
		this.contactos = contactos;
		this.calendario = calendario;
		this.versionMatriz = versionMatriz;
		this.minutosTendencia = minutosTendencia;
		this.severidadTendencia = severidadTendencia;
		this.reloj = reloj;
	}

	/**
	 * @return la configuración vigente del paciente, con su versión
	 * @throws cl.ucn.alertacuida.application.comun.error.NoEncontrado si el paciente no existe
	 */
	public ConfiguracionReloj armar(UUID idPaciente) {
		PerfilClinico perfil = perfiles.consultar(idPaciente);
		List<ContactoConfigurado> lista = contactos.listar(idPaciente).contactos().stream()
				.filter(ContactoDelPaciente::disponible)
				.map(contacto -> new ContactoConfigurado(contacto.id(), contacto.persona().nombre(),
						contacto.vinculo().tipo(), contacto.prioridad(), contacto.vinculo().horario(),
						contacto.canales()))
				.toList();
		return ConfiguracionReloj.de(new ContenidoConfiguracion(perfil.ajustes().modulos(),
				perfil.ajustes().gpsActivo(), perfil.ajustes().modoConservador(), perfil.fc().umbral(),
				perfil.spo2().umbral(), zonaQueViaja(perfil), versionMatriz, minutosTendencia, severidadTendencia,
				perfil.minutosEspera(), RONDAS, lista, calendario.horario(), feriadosQueViajan()));
	}

	/**
	 * La zona viaja solo con el módulo activo: así el reloj no recibe la ubicación del domicilio si
	 * no la usa, y cambiar una zona apagada no cambia la versión.
	 */
	private static ZonaSegura zonaQueViaja(PerfilClinico perfil) {
		return perfil.ajustes().modulos().zonaSegura() && perfil.zonaSegura() != null
				? perfil.zonaSegura().zona() : null;
	}

	@Override
	public String de(UUID idPaciente) {
		return armar(idPaciente).version();
	}

	/**
	 * Los feriados del año en curso y el siguiente, en hora de Chile. Se cuentan por año calendario y
	 * no desde hoy: así la versión cambia una vez al año (el 1 de enero) y no cada vez que pasa un feriado.
	 */
	private List<LocalDate> feriadosQueViajan() {
		int anio = LocalDate.now(reloj.withZone(ZonaHoraria.CHILE)).getYear();
		return calendario.feriados().stream()
				.map(Feriado::fecha)
				.filter(fecha -> fecha.getYear() == anio || fecha.getYear() == anio + 1)
				.toList();
	}

}
