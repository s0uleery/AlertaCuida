package cl.ucn.alertacuida.adapters.persistencia.personal;

import cl.ucn.alertacuida.application.personal.puerto.ContrasenasGuardadas;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** El hash de la contraseña de una cuenta, en usuario.hash_contrasena (bcrypt). */
@Repository
public class ContrasenasGuardadasJdbc implements ContrasenasGuardadas {

	private final JdbcClient jdbc;

	/** @param jdbc cliente de Spring sobre la conexión a PostgreSQL */
	public ContrasenasGuardadasJdbc(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Optional<String> hashDe(UUID idUsuario) {
		return jdbc.sql("SELECT hash_contrasena FROM usuario WHERE id_usuario = :id AND hash_contrasena IS NOT NULL")
				.param("id", idUsuario)
				.query(String.class)
				.optional();
	}

}
