package cl.ucn.alertacuida.application.reloj.modelo;

import java.util.UUID;

/** Un reloj guardado en el inventario (tabla smartwatch). */
public record Reloj(UUID id, DatosReloj datos) {
}
