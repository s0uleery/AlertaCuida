package cl.ucn.alertacuida.application.sesion;

import cl.ucn.alertacuida.application.sesion.puerto.CuentaUsuarioRepository;
import cl.ucn.alertacuida.application.sesion.puerto.EmisorToken;
import cl.ucn.alertacuida.application.sesion.puerto.HashRun;
import cl.ucn.alertacuida.application.sesion.puerto.RegistroAccesos;
import cl.ucn.alertacuida.application.sesion.puerto.VerificadorContrasena;
import cl.ucn.alertacuida.domain.personal.Run;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/** Inicio de sesión del personal con RUN y contraseña (1.3.3 y 1.3.6). El segundo factor llega en 1.3.5. */
public class IniciarSesion {

	private final CuentaUsuarioRepository cuentas;
	private final RegistroAccesos accesos;
	private final HashRun hashRun;
	private final VerificadorContrasena verificador;
	private final EmisorToken emisor;
	private final PoliticaBloqueo bloqueo;
	private final Clock reloj;

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

	/** @throws InicioSesionFallido con el mismo mensaje para cualquier causa */
	public SesionIniciada iniciar(String textoRun, String contrasena, String ip) {
		Instant ahora = reloj.instant();
		Optional<CuentaUsuario> encontrada = buscar(textoRun);
		if (encontrada.isEmpty()) {
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

	private Optional<CuentaUsuario> buscar(String textoRun) {
		try {
			return cuentas.buscarPorRunHash(hashRun.de(Run.de(textoRun)));
		} catch (IllegalArgumentException runInvalido) {
			return Optional.empty();
		}
	}

}
