package cl.ucn.alertacuida.application.configuracion.modelo;

import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.contacto.Canal;
import cl.ucn.alertacuida.domain.contacto.Horario;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Todo lo que el reloj necesita para evaluar y escalar sin conexión (esquema Configuracion de
 * docs/api-reloj.yaml), sin la versión, que se calcula de este contenido con {@link #huella()}.
 *
 * @param modulos         qué vigila el reloj
 * @param gpsActivo       si envía su ubicación (solo para ubicar a la persona)
 * @param modoConservador si el reloj alerta con criterios más estrictos
 * @param fc              umbral de la frecuencia cardíaca (propio o base)
 * @param spo2            umbral de la saturación de oxígeno (propio o base)
 * @param versionMatriz   versión de la matriz FC × SpO2 del Anexo B
 * @param minutosEspera   antes de pasar al siguiente contacto
 * @param rondas          veces que el reloj recorre la lista de contactos
 * @param contactos       activos y con algún canal usable, por prioridad
 * @param horarioHabil    tramo de cada día hábil, de lunes a domingo
 * @param feriados        del año en curso y el siguiente, ordenados
 */
public record ContenidoConfiguracion(Modulos modulos, boolean gpsActivo, boolean modoConservador, Umbral fc,
		Umbral spo2, String versionMatriz, int minutosEspera, int rondas, List<ContactoConfigurado> contactos,
		Map<DiaSemana, TramoHabil> horarioHabil, List<LocalDate> feriados) {

	/** Largo de la versión: 16 caracteres hexadecimales del SHA-256 (64 bits). */
	static final int LARGO_VERSION = 16;

	public ContenidoConfiguracion {
		contactos = List.copyOf(contactos);
		horarioHabil = Collections.unmodifiableMap(new EnumMap<>(horarioHabil));
		feriados = List.copyOf(feriados);
	}

	/**
	 * La versión de esta configuración: los primeros {@value #LARGO_VERSION} caracteres del SHA-256
	 * de su texto canónico. Cambia con cualquier dato, aunque sea el nombre o el horario de un
	 * contacto, y es la misma en cada cálculo mientras nada cambie.
	 *
	 * <p>Se calcula sobre un texto armado aquí, campo por campo, y no sobre {@code toString()}, cuyo
	 * formato puede cambiar entre versiones de Java y haría descargar la configuración a todos los relojes.
	 *
	 * @return la versión, por ejemplo "9f2c4e1a7b3d5e60"
	 */
	public String huella() {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(textoCanonico().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash).substring(0, LARGO_VERSION);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("Java siempre trae SHA-256", e);
		}
	}

	/** @return una línea por dato, en orden fijo */
	String textoCanonico() {
		StringBuilder texto = new StringBuilder()
				.append("modulos=").append(modulos.caidas()).append(',').append(modulos.fc()).append(',')
				.append(modulos.spo2()).append(',').append(modulos.inactividad()).append(',')
				.append(modulos.tendencia()).append('\n')
				.append("gps=").append(gpsActivo).append('\n')
				.append("conservador=").append(modoConservador).append('\n')
				.append("fc=").append(limites(fc)).append('\n')
				.append("spo2=").append(limites(spo2)).append('\n')
				.append("matriz=").append(versionMatriz).append('\n')
				.append("espera=").append(minutosEspera).append('\n')
				.append("rondas=").append(rondas).append('\n');
		for (ContactoConfigurado contacto : contactos) {
			Horario horario = contacto.horario();
			texto.append("contacto=").append(contacto.idContacto()).append('|').append(contacto.nombre()).append('|')
					.append(contacto.prioridad()).append('|')
					.append(horario == null ? "" : horario.inicio() + "-" + horario.fin()).append('|')
					.append(contacto.canales().stream().map(Canal::name).collect(Collectors.joining(",")))
					.append('\n');
		}
		horarioHabil.forEach((dia, tramo) -> texto.append("habil=").append(dia).append(' ').append(tramo.inicio())
				.append('-').append(tramo.fin()).append('\n'));
		feriados.forEach(feriado -> texto.append("feriado=").append(feriado).append('\n'));
		return texto.toString();
	}

	/** @return "graveBajo|normalMin|normalMax|graveAlto", con los vacíos como "" */
	private static String limites(Umbral umbral) {
		return String.join("|", plano(umbral.graveBajo()), plano(umbral.normalMin()), plano(umbral.normalMax()),
				plano(umbral.graveAlto()));
	}

	private static String plano(BigDecimal valor) {
		return valor == null ? "" : valor.toPlainString();
	}

}
