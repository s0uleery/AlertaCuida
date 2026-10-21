package cl.ucn.alertacuida.application.sesion;

import java.time.Instant;

/** Token de acceso de vida corta (1.3.3). */
public record TokenAcceso(String valor, Instant expiraEn) {
}
