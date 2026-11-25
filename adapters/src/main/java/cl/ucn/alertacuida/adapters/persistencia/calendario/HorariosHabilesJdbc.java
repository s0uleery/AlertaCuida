package cl.ucn.alertacuida.adapters.persistencia.calendario;

import cl.ucn.alertacuida.application.calendario.puerto.HorariosHabiles;
import cl.ucn.alertacuida.domain.calendario.DiaSemana;
import cl.ucn.alertacuida.domain.calendario.TramoHabil;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** El horario hábil en la tabla horario_habil (V18). */
@Repository
public class HorariosHabilesJdbc implements HorariosHabiles {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public HorariosHabilesJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<DiaSemana, TramoHabil> leer() {
		Map<DiaSemana, TramoHabil> horario = new EnumMap<>(DiaSemana.class);
		jdbc.sql("SELECT dia_semana, inicio, fin FROM horario_habil")
				.query((fila, n) -> {
					horario.put(DiaSemana.valueOf(fila.getString("dia_semana")), new TramoHabil(
							fila.getObject("inicio", LocalTime.class), fila.getObject("fin", LocalTime.class)));
					return null;
				})
				.list();
		return horario;
	}

	/** Borra y vuelve a insertar: son a lo más siete filas. */
	@Override
	public void guardar(Map<DiaSemana, TramoHabil> horario) {
		jdbc.sql("DELETE FROM horario_habil").update();
		horario.forEach((dia, tramo) -> jdbc
				.sql("INSERT INTO horario_habil (dia_semana, inicio, fin) VALUES (:d, :i, :f)")
				.param("d", dia.name())
				.param("i", tramo.inicio())
				.param("f", tramo.fin())
				.update());
	}

}
