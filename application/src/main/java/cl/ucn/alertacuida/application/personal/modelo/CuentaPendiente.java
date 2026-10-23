package cl.ucn.alertacuida.application.personal.modelo;

import java.util.UUID;

/** Cuenta creada que aún no tiene contraseña. */
public record CuentaPendiente(UUID id, String runHash) {
}
