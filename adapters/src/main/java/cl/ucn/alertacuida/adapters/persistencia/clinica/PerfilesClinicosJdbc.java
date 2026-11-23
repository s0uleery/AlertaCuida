package cl.ucn.alertacuida.adapters.persistencia.clinica;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.ConfiguracionGuardada;
import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import cl.ucn.alertacuida.application.clinica.modelo.UmbralGuardado;
import cl.ucn.alertacuida.application.clinica.modelo.ZonaGuardada;
import cl.ucn.alertacuida.application.clinica.puerto.PerfilesClinicos;
import cl.ucn.alertacuida.domain.alerta.Severidad;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.clinica.ZonaSegura;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Historial de configuración, umbrales y zona segura en las tablas configuracion, umbral y
 * zona_segura. Solo agrega filas: las anteriores no se modifican. El motivo del cambio y las
 * coordenadas de la zona se cifran aquí.
 */
@Repository
public class PerfilesClinicosJdbc implements PerfilesClinicos {

	private final JdbcClient jdbc;
	private final CifradoCampos cifrado;

	/**
	 * @param jdbc    cliente de Spring sobre la conexión a PostgreSQL
	 * @param cifrado cifra y descifra el motivo del cambio
	 */
	public PerfilesClinicosJdbc(JdbcClient jdbc, CifradoCampos cifrado) {
		this.jdbc = jdbc;
		this.cifrado = cifrado;
	}

	@Override
	public Optional<ConfiguracionGuardada> configuracionVigente(UUID idPaciente) {
		return jdbc.sql("""
				SELECT * FROM configuracion WHERE id_paciente = :id
				ORDER BY vigente_desde DESC LIMIT 1""")
				.param("id", idPaciente)
				.query((fila, n) -> new ConfiguracionGuardada(new AjustesClinicos(
						new Modulos(fila.getBoolean("caidas_activa"), fila.getBoolean("fc_activa"),
								fila.getBoolean("spo2_activa"), fila.getBoolean("inactividad_activa"),
								fila.getBoolean("tendencia_activa"), fila.getBoolean("zona_segura_activa"),
								fila.getBoolean("boton_ayuda_activo")),
						fila.getBoolean("gps_activo"), fila.getBoolean("modo_conservador"),
						fila.getObject("minutos_espera", Integer.class),
						cifrado.descifrar(fila.getString("motivo_cambio"), ColumnaCifrada.CONFIGURACION_MOTIVO)),
						fila.getTimestamp("vigente_desde").toInstant()))
				.optional();
	}

	@Override
	public void guardarConfiguracion(UUID idPaciente, Instant vigenteDesde, AjustesClinicos ajustes) {
		Modulos modulos = ajustes.modulos();
		jdbc.sql("""
				INSERT INTO configuracion (id_paciente, vigente_desde, caidas_activa, fc_activa, spo2_activa,
					inactividad_activa, tendencia_activa, zona_segura_activa, boton_ayuda_activo, gps_activo,
					modo_conservador, minutos_espera, motivo_cambio)
				VALUES (:paciente, :desde, :caidas, :fc, :spo2, :inactividad, :tendencia, :zona, :boton, :gps,
					:conservador, :espera, :motivo)""")
				.param("paciente", idPaciente)
				.param("desde", Timestamp.from(vigenteDesde))
				.param("caidas", modulos.caidas())
				.param("fc", modulos.fc())
				.param("spo2", modulos.spo2())
				.param("inactividad", modulos.inactividad())
				.param("tendencia", modulos.tendencia())
				.param("zona", modulos.zonaSegura())
				.param("boton", modulos.botonAyuda())
				.param("gps", ajustes.gpsActivo())
				.param("conservador", ajustes.modoConservador())
				.param("espera", ajustes.minutosEspera())
				.param("motivo", cifrado.cifrar(ajustes.motivo(), ColumnaCifrada.CONFIGURACION_MOTIVO))
				.update();
	}

	@Override
	public Optional<UmbralGuardado> umbralVigente(UUID idPaciente, TipoLectura variable) {
		return jdbc.sql("""
				SELECT * FROM umbral WHERE id_paciente = :id AND variable = :variable
				ORDER BY vigente_desde DESC LIMIT 1""")
				.param("id", idPaciente)
				.param("variable", variable.name())
				.query((fila, n) -> new UmbralGuardado(fila.getBigDecimal("grave_bajo") == null ? null
						: Umbral.de(variable, fila.getBigDecimal("grave_bajo"), fila.getBigDecimal("normal_min"),
								fila.getBigDecimal("normal_max"), fila.getBigDecimal("grave_alto")),
						fila.getTimestamp("vigente_desde").toInstant()))
				.optional();
	}

	@Override
	public void guardarUmbral(UUID idPaciente, TipoLectura variable, Instant vigenteDesde, Umbral umbral) {
		jdbc.sql("""
				INSERT INTO umbral (id_paciente, variable, vigente_desde, grave_bajo, normal_min, normal_max,
					grave_alto)
				VALUES (:paciente, :variable, :desde, :graveBajo, :normalMin, :normalMax, :graveAlto)""")
				.param("paciente", idPaciente)
				.param("variable", variable.name())
				.param("desde", Timestamp.from(vigenteDesde))
				.param("graveBajo", umbral == null ? null : umbral.graveBajo())
				.param("normalMin", umbral == null ? null : umbral.normalMin())
				.param("normalMax", umbral == null ? null : umbral.normalMax())
				.param("graveAlto", umbral == null ? null : umbral.graveAlto())
				.update();
	}

	@Override
	public Optional<ZonaGuardada> zonaVigente(UUID idPaciente) {
		return jdbc.sql("""
				SELECT * FROM zona_segura WHERE id_paciente = :id
				ORDER BY vigente_desde DESC LIMIT 1""")
				.param("id", idPaciente)
				.query((fila, n) -> new ZonaGuardada(ZonaSegura.de(
						coordenada(fila.getString("latitud"), ColumnaCifrada.ZONA_SEGURA_LATITUD),
						coordenada(fila.getString("longitud"), ColumnaCifrada.ZONA_SEGURA_LONGITUD),
						fila.getInt("radio_metros"), Severidad.valueOf(fila.getString("severidad"))),
						fila.getTimestamp("vigente_desde").toInstant()))
				.optional();
	}

	/** @return la coordenada descifrada */
	private BigDecimal coordenada(String cifrada, ColumnaCifrada columna) {
		return new BigDecimal(cifrado.descifrar(cifrada, columna));
	}

	/** Las coordenadas se cifran como texto, sin notación científica. */
	@Override
	public void guardarZona(UUID idPaciente, Instant vigenteDesde, ZonaSegura zona) {
		jdbc.sql("""
				INSERT INTO zona_segura (id_paciente, vigente_desde, latitud, longitud, radio_metros, severidad)
				VALUES (:paciente, :desde, :latitud, :longitud, :radio, :severidad)""")
				.param("paciente", idPaciente)
				.param("desde", Timestamp.from(vigenteDesde))
				.param("latitud", cifrado.cifrar(zona.latitud().toPlainString(), ColumnaCifrada.ZONA_SEGURA_LATITUD))
				.param("longitud", cifrado.cifrar(zona.longitud().toPlainString(), ColumnaCifrada.ZONA_SEGURA_LONGITUD))
				.param("radio", zona.radioMetros())
				.param("severidad", zona.severidad().name())
				.update();
	}

}
