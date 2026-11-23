package cl.ucn.alertacuida.application.alerta.modelo;

/**
 * Una institución a la que se puede derivar un caso (catálogo destino_derivacion).
 *
 * @param codigo por ejemplo CESFAM
 * @param nombre como se muestra
 */
public record DestinoDerivacion(String codigo, String nombre) {
}
