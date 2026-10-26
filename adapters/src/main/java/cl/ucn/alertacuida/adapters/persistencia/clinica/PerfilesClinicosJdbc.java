package cl.ucn.alertacuida.adapters.persistencia.clinica;

import cl.ucn.alertacuida.adapters.seguridad.cifrado.CifradoCampos;
import cl.ucn.alertacuida.adapters.seguridad.cifrado.ColumnaCifrada;
import cl.ucn.alertacuida.application.clinica.modelo.AjustesClinicos;
import cl.ucn.alertacuida.application.clinica.modelo.ConfiguracionGuardada;
import cl.ucn.alertacuida.application.clinica.modelo.Modulos;
import cl.ucn.alertacuida.application.clinica.modelo.UmbralGuardado;
import cl.ucn.alertacuida.application.clinica.puerto.PerfilesClinicos;
import cl.ucn.alertacuida.domain.clinica.Umbral;
import cl.ucn.alertacuida.domain.telemetria.TipoLectura;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Historial de configuración y umbrales en las tablas configuracion y umbral. Solo agrega filas:
 * las anteriores no se modifican. El motivo del cambio se cifra aquí.
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
								fila.getBoolean("tendencia_activa")),
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
					inactividad_activa, tendencia_activa, gps_activo, modo_conservador, minutos_espera, motivo_cambio)
				VALUES (:paciente, :desde, :caidas, :fc, :spo2, :inactividad, :tendencia, :gps, :conservador,
					:espera, :motivo)""")
				.param("paciente", idPaciente)
				.param("desde", Timestamp.from(vigenteDesde))
				.param("caidas", modulos.caidas())
				.param("fc", modulos.fc())
				.param("spo2", modulos.spo2())
				.param("inactividad", modulos.inactividad())
				.param("tendencia", modulos.tendencia())
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

}
