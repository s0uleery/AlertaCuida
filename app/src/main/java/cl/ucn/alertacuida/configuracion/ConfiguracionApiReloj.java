package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.alerta.RecibirAlerta;
import cl.ucn.alertacuida.application.alerta.RevisionDiscrepancias;
import cl.ucn.alertacuida.application.alerta.puerto.Alertas;
import cl.ucn.alertacuida.application.asignacion.AutenticarReloj;
import cl.ucn.alertacuida.application.asignacion.puerto.Asignaciones;
import cl.ucn.alertacuida.application.clinica.PerfilClinicoPaciente;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.configuracion.ConfiguracionDelReloj;
import cl.ucn.alertacuida.application.configuracion.ConsultarConfiguracionAplicada;
import cl.ucn.alertacuida.application.contacto.ContactosPaciente;
import cl.ucn.alertacuida.application.reloj.RegistrarTokenFcm;
import cl.ucn.alertacuida.application.reloj.puerto.Relojes;
import cl.ucn.alertacuida.application.telemetria.RecibirSincronizacion;
import cl.ucn.alertacuida.application.telemetria.puerto.Sincronizaciones;
import cl.ucn.alertacuida.domain.calendario.CalendarioHabil;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso de la API del reloj, unidos con sus adaptadores. Igual que
 * {@link ConfiguracionPacientesYRelojes}, que tiene los del dashboard.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesAlertas.class)
public class ConfiguracionApiReloj {

	/** Reconoce al reloj por su token en cada petición. */
	@Bean
	AutenticarReloj autenticarReloj(Asignaciones asignaciones, SecretosUnSoloUso secretos) {
		return new AutenticarReloj(asignaciones, secretos);
	}

	/**
	 * Configuración que descarga el reloj, armada del perfil clínico, los contactos y el calendario.
	 * También da la versión vigente a la sincronización.
	 */
	@Bean
	ConfiguracionDelReloj configuracionDelReloj(PerfilClinicoPaciente perfiles, ContactosPaciente contactos,
			CalendarioHabil calendario, PropiedadesClinicas propiedades, Clock reloj) {
		return new ConfiguracionDelReloj(perfiles, contactos, calendario, propiedades.versionMatriz(),
				propiedades.minutosTendencia(), propiedades.severidadTendencia(), reloj);
	}

	/** Lotes de estado y lecturas que el reloj envía cada 2 horas. */
	@Bean
	RecibirSincronizacion recibirSincronizacion(Sincronizaciones sincronizaciones,
			ConfiguracionDelReloj configuraciones, Transacciones transacciones, Clock reloj) {
		return new RecibirSincronizacion(sincronizaciones, configuraciones, transacciones, reloj);
	}

	/** Compara cada alerta nueva con la configuración vigente del paciente. */
	@Bean
	RevisionDiscrepancias revisionDiscrepancias(ConfiguracionDelReloj configuraciones, PerfilClinicoPaciente perfiles,
			PropiedadesClinicas propiedades) {
		return new RevisionDiscrepancias(configuraciones, perfiles, propiedades.severidadTendencia());
	}

	/** Alertas del reloj, cancelaciones del paciente, agravamiento y estado de cada alerta. */
	@Bean
	RecibirAlerta recibirAlerta(Alertas alertas, RevisionDiscrepancias revision, PropiedadesAlertas propiedades,
			Transacciones transacciones, Clock reloj) {
		return new RecibirAlerta(alertas, revision, propiedades.limite(), transacciones, reloj);
	}

	/** Token de Firebase de cada reloj, para enviarle avisos. */
	@Bean
	RegistrarTokenFcm registrarTokenFcm(Relojes relojes, Clock reloj) {
		return new RegistrarTokenFcm(relojes, reloj);
	}

	/** Si el reloj de un paciente ya aplica la configuración vigente (ficha del dashboard). */
	@Bean
	ConsultarConfiguracionAplicada consultarConfiguracionAplicada(ConfiguracionDelReloj configuraciones,
			Asignaciones asignaciones, Sincronizaciones sincronizaciones) {
		return new ConsultarConfiguracionAplicada(configuraciones, asignaciones, sincronizaciones);
	}

}
