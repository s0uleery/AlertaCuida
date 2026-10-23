package cl.ucn.alertacuida.configuracion;

import cl.ucn.alertacuida.adapters.seguridad.PropiedadesSeguridad;
import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.comun.puerto.SecretosUnSoloUso;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.personal.ActivarCuenta;
import cl.ucn.alertacuida.application.personal.CrearCuentaPersonal;
import cl.ucn.alertacuida.application.personal.EmisorEnlacesActivacion;
import cl.ucn.alertacuida.application.personal.NuevoEnlaceActivacion;
import cl.ucn.alertacuida.application.personal.puerto.CuentasPersonal;
import cl.ucn.alertacuida.application.personal.puerto.HashContrasena;
import cl.ucn.alertacuida.application.personal.puerto.TokensActivacion;
import cl.ucn.alertacuida.application.sesion.IniciarSesion;
import cl.ucn.alertacuida.application.sesion.PoliticaBloqueo;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import cl.ucn.alertacuida.application.sesion.puerto.EmisorToken;
import cl.ucn.alertacuida.application.sesion.puerto.RegistroAccesos;
import cl.ucn.alertacuida.application.sesion.puerto.VerificadorContrasena;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Une los casos de uso (Java puro, en application) con sus adaptadores. Es el único lugar
 * que conoce ambos lados: los casos de uso solo ven interfaces.
 *
 * <p>Spring busca por tipo un adaptador para cada parámetro de estos métodos (por ejemplo,
 * {@code CuentasPersonalJdbc} para {@link CuentasPersonal}) y lo pasa al constructor.
 */
@Configuration
public class ConfiguracionCasosDeUso {

	/** Hora actual en UTC. Es un bean para que las pruebas puedan fijar la hora. */
	@Bean
	Clock reloj() {
		return Clock.systemUTC();
	}

	/** Inicio de sesión con RUN y contraseña (1.3.3, 1.3.6). */
	@Bean
	IniciarSesion iniciarSesion(CuentaUsuarioRepository cuentas, RegistroAccesos accesos, HashRun hashRun,
			VerificadorContrasena verificador, EmisorToken emisor, PropiedadesSeguridad propiedades, Clock reloj) {
		PropiedadesSeguridad.Bloqueo bloqueo = propiedades.bloqueo();
		return new IniciarSesion(cuentas, accesos, hashRun, verificador, emisor,
				new PoliticaBloqueo(bloqueo.intentos(), bloqueo.ventana()), reloj);
	}

	/** Emisor de enlaces de activación, compartido por el alta y por el enlace nuevo. */
	@Bean
	EmisorEnlacesActivacion emisorEnlacesActivacion(TokensActivacion tokens, SecretosUnSoloUso secretos,
			PropiedadesSeguridad propiedades) {
		return new EmisorEnlacesActivacion(tokens, secretos, propiedades.activacion().vigencia());
	}

	/** Alta de cuentas del personal (1.3.2). */
	@Bean
	CrearCuentaPersonal crearCuentaPersonal(CuentasPersonal cuentas, HashRun hashRun,
			EmisorEnlacesActivacion enlaces, RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new CrearCuentaPersonal(cuentas, hashRun, enlaces, auditoria, transacciones, reloj);
	}

	/** Reemplazo de un enlace de activación perdido o vencido (1.3.2). */
	@Bean
	NuevoEnlaceActivacion nuevoEnlaceActivacion(CuentasPersonal cuentas, EmisorEnlacesActivacion enlaces,
			RegistroAuditoria auditoria, Transacciones transacciones, Clock reloj) {
		return new NuevoEnlaceActivacion(cuentas, enlaces, auditoria, transacciones, reloj);
	}

	/** Activación de la cuenta con el enlace (1.3.2). */
	@Bean
	ActivarCuenta activarCuenta(TokensActivacion tokens, SecretosUnSoloUso secretos, CuentasPersonal cuentas,
			HashRun hashRun, HashContrasena hashContrasena, RegistroAuditoria auditoria, Transacciones transacciones,
			Clock reloj) {
		return new ActivarCuenta(tokens, secretos, cuentas, hashRun, hashContrasena, auditoria, transacciones, reloj);
	}

}
