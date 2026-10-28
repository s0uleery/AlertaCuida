package cl.ucn.alertacuida.application.configuracion.modelo;

/**
 * La configuración vigente de un paciente, lista para el reloj.
 *
 * @param version   hash del contenido; el reloj la informa en cada sincronización
 * @param contenido lo que el reloj aplica
 */
public record ConfiguracionReloj(String version, ContenidoConfiguracion contenido) {

	/** @return la configuración con la versión calculada de su contenido */
	public static ConfiguracionReloj de(ContenidoConfiguracion contenido) {
		return new ConfiguracionReloj(contenido.huella(), contenido);
	}

}
