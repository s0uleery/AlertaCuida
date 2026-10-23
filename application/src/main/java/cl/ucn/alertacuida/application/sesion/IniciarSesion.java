package cl.ucn.alertacuida.application.sesion;

import cl.ucn.alertacuida.application.comun.puerto.HashRun;
import cl.ucn.alertacuida.application.sesion.error.InicioSesionFallido;
import cl.ucn.alertacuida.application.sesion.modelo.CuentaUsuario;
import cl.ucn.alertacuida.application.sesion.modelo.SesionIniciada;
import cl.ucn.alertacuida.application.sesion.modelo.UsuarioSesion;
import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import cl.ucn.alertacuida.application.sesion.puerto.EmisorToken;
import cl.ucn.alertacuida.application.sesion.puerto.RegistroAccesos;
import cl.ucn.alertacuida.application.sesion.puerto.VerificadorContrasena;
import cl.ucn.alertacuida.domain.comun.Run;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * Inicio de sesión del personal con RUN y contraseña. El segundo factor (TOTP) se agregará más adelante.
 *
 * <p>Pasos: buscar la cuenta por el hash del RUN, revisar si está bloqueada por intentos fallidos,
 * comparar la contraseña y, si todo está bien, emitir el token con los permisos del rol. Cada intento,
 * correcto o no, queda en la auditoría.
 */
public class IniciarSesion {

	private final CuentaUsuarioRepository cuentas;
	private final RegistroAccesos accesos;
	private final HashRun hashRun;
	private final VerificadorContrasena verificador;
	private final EmisorToken emisor;
	private final PoliticaBloqueo bloqueo;
	private final Clock reloj;

	/** Recibe los puertos que usa; los arma ConfiguracionCasosDeUso. */
	public IniciarSesion(CuentaUsuarioRepository cuentas, RegistroAccesos accesos, HashRun hashRun,
			VerificadorContrasena verificador, EmisorToken emisor, PoliticaBloqueo bloqueo, Clock reloj) {
		this.cuentas = cuentas;
		this.accesos = accesos;
		this.hashRun = hashRun;
		this.verificador = verificador;
		this.emisor = emisor;
		this.bloqueo = bloqueo;
		this.reloj = reloj;
	}

	/**
	 * @param textoRun   RUN tal como lo escribió la persona, con o sin puntos
	 * @param contrasena contraseña escrita
	 * @param ip         desde dónde llegó la petición, para la auditoría
	 * @return el token de acceso y los datos del usuario con sus permisos
	 * @throws InicioSesionFallido con el mismo mensaje para cualquier causa
	 */
	public SesionIniciada iniciar(String textoRun, String contrasena, String ip) {
		Instant ahora = reloj.instant();
		Optional<CuentaUsuario> encontrada = buscar(textoRun);
		if (encontrada.isEmpty()) {
			// Se compara igual (contra un hash de relleno) para tardar lo mismo que con un RUN existente
			verificador.coincide(contrasena, null);
			accesos.registrarLoginFallido(null, ip, ahora);
			throw new InicioSesionFallido();
		}
		CuentaUsuario cuenta = encontrada.get();
		boolean bloqueada = bloqueo.bloquea(accesos.contarFallidosRecientes(cuenta.id(), bloqueo.inicioVentana(ahora)));
		boolean correcta = contrasena != null && verificador.coincide(contrasena, cuenta.hashContrasena());
		if (bloqueada || !cuenta.activo() || !correcta) {
			accesos.registrarLoginFallido(cuenta.id(), ip, ahora);
			throw new InicioSesionFallido();
		}
		accesos.registrarLogin(cuenta.id(), ip, ahora);
		UsuarioSesion usuario = new UsuarioSesion(cuenta.id(), cuenta.nombre(), cuenta.codigoRol(),
				cuentas.permisosDelRol(cuenta.codigoRol()));
		return new SesionIniciada(emisor.emitir(usuario), usuario);
	}

	/** Un RUN mal escrito se trata como inexistente: así la respuesta es la misma. */
	private Optional<CuentaUsuario> buscar(String textoRun) {
		try {
			return cuentas.buscarPorRunHash(hashRun.de(Run.de(textoRun)));
		} catch (IllegalArgumentException runInvalido) {
			return Optional.empty();
		}
	}

}
