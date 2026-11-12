package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.clinica.PerfilClinicoPaciente;
import cl.ucn.alertacuida.application.clinica.modelo.ValoresBase;
import cl.ucn.alertacuida.application.clinica.puerto.PerfilesClinicos;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.contacto.ContactosPaciente;
import cl.ucn.alertacuida.application.contacto.puerto.AvisoCambioContactos;
import cl.ucn.alertacuida.application.contacto.puerto.Contactos;
import cl.ucn.alertacuida.application.contacto.puerto.HashMedioContacto;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Casos de uso del perfil clínico y de los contactos de cada paciente, unidos con sus adaptadores.
 * Igual que {@link ConfiguracionPacientesYRelojes}.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesClinicas.class)
public class ConfiguracionPerfilYContactos {

	/**
	 * Valores base de application.properties. Se validan con las mismas reglas que los de un
	 * paciente: si están mal, el backend no arranca.
	 */
	@Bean
	ValoresBase valoresBase(PropiedadesClinicas propiedades) {
		PropiedadesClinicas.Fc fc = propiedades.umbralFc();
		PropiedadesClinicas.Spo2 spo2 = propiedades.umbralSpo2();
		return new ValoresBase(
				Umbral.de(TipoLectura.FC, fc.graveBajo(), fc.normalMin(), fc.normalMax(), fc.graveAlto()),
				Umbral.de(TipoLectura.SPO2, spo2.graveBajo(), spo2.normalMin(), null, null),
				propiedades.minutosEspera());
	}

	/** Módulos, opciones y umbrales de cada paciente. */
	@Bean
	PerfilClinicoPaciente perfilClinicoPaciente(PerfilesClinicos perfiles, Pacientes pacientes, ValoresBase base,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new PerfilClinicoPaciente(perfiles, pacientes, base, auditoria, transacciones, reloj);
	}

	/** Lista de contactos de cada paciente. */
	@Bean
	ContactosPaciente contactosPaciente(Contactos contactos, Pacientes pacientes, HashMedioContacto hash,
			AvisoCambioContactos avisoCambio,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new ContactosPaciente(contactos, pacientes, hash, auditoria, transacciones, avisoCambio, reloj);
	}

}
