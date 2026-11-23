package cl.ucn.alertacuida.application.personal.puerto;

import cl.ucn.alertacuida.application.personal.modelo.CuentaEquipo;
import cl.ucn.alertacuida.application.personal.modelo.PerfilEquipo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cuentas del equipo ya creadas (tabla usuario), tal como las administra la sección Administración.
 * Va aparte de {@link CuentasPersonal}, que solo sirve al alta y a la activación.
 */
public interface EquipoPersonal {

	/** @return todas las cuentas, por nombre */
	List<PerfilEquipo> listar();

	/**
	 * @param id cuenta buscada
	 * @return la cuenta, si existe
	 */
	Optional<CuentaEquipo> buscar(UUID id);

	/**
	 * @param excluida la cuenta que está por dejar de ser administradora
	 * @return si queda otra cuenta activa con el rol de sistema (el administrador)
	 */
	boolean hayOtroAdministradorActivo(UUID excluida);

	/** Guarda el rol nuevo de la cuenta. */
	void cambiarRol(UUID id, String codigoRol);

	/**
	 * Guarda el celular con WhatsApp de la cuenta. El adaptador lo cifra.
	 *
	 * @param telefono   normalizado (+569...)
	 * @param fechaOptIn cuándo aceptó recibir WhatsApp, o vacío si no lo aceptó
	 */
	void guardarTelefono(UUID id, String telefono, Instant fechaOptIn);

	/**
	 * @param recibe si recibe por correo los avisos de las alertas técnicas
	 * @return false si la cuenta no existe
	 */
	boolean cambiarRecibeAvisos(UUID id, boolean recibe);

}
