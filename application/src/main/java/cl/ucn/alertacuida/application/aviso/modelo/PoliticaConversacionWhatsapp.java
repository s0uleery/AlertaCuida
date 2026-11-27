package cl.ucn.alertacuida.application.aviso.modelo;

import cl.ucn.alertacuida.domain.alerta.Severidad;
import java.time.Duration;
import java.util.Objects;

/**
 * Plazos y topes de recordatorios del flujo conversacional de WhatsApp (decisiones del 26-11-2026):
 * <ul>
 * <li>Límite para responder tras "Llamaré al paciente": 10 min.</li>
 * <li>Recordatorios de llegada (independientes de si se ignoró el anterior):
 * LEVE cada 30 min (máx. 5 = 2 h 30 min), MEDIA cada 15 min (máx. 5 = 1 h 15 min),
 * GRAVE cada 5 min (máx. 8 = 40 min).</li>
 * </ul>
 *
 * @param plazoLlamada   límite para contestar el estado del paciente tras tomar la llamada
 * @param intervaloLeve  tiempo entre recordatorios de llegada en alerta LEVE
 * @param maximoLeve     cantidad máxima de intervalos en LEVE antes de escalar
 * @param intervaloMedia tiempo entre recordatorios de llegada en alerta MEDIA
 * @param maximoMedia    cantidad máxima de intervalos en MEDIA antes de escalar
 * @param intervaloGrave tiempo entre recordatorios de llegada en alerta GRAVE
 * @param maximoGrave    cantidad máxima de intervalos en GRAVE antes de escalar
 */
public record PoliticaConversacionWhatsapp(Duration plazoLlamada, Duration intervaloLeve, int maximoLeve,
		Duration intervaloMedia, int maximoMedia, Duration intervaloGrave, int maximoGrave) {

	/** Valores acordados por el equipo para el piloto. */
	public static final PoliticaConversacionWhatsapp POR_DEFECTO = new PoliticaConversacionWhatsapp(
			Duration.ofMinutes(10), Duration.ofMinutes(30), 5, Duration.ofMinutes(15), 5, Duration.ofMinutes(5), 8);

	/** Valida que todos los plazos y máximos sean positivos. */
	public PoliticaConversacionWhatsapp {
		Objects.requireNonNull(plazoLlamada, "plazoLlamada");
		Objects.requireNonNull(intervaloLeve, "intervaloLeve");
		Objects.requireNonNull(intervaloMedia, "intervaloMedia");
		Objects.requireNonNull(intervaloGrave, "intervaloGrave");
		if (plazoLlamada.isNegative() || plazoLlamada.isZero()
				|| intervaloLeve.isNegative() || intervaloLeve.isZero()
				|| intervaloMedia.isNegative() || intervaloMedia.isZero()
				|| intervaloGrave.isNegative() || intervaloGrave.isZero()) {
			throw new IllegalArgumentException("Los plazos de conversación de WhatsApp deben ser positivos");
		}
		if (maximoLeve < 1 || maximoMedia < 1 || maximoGrave < 1) {
			throw new IllegalArgumentException("Los máximos de recordatorios deben ser al menos 1");
		}
	}

	/** @return intervalo entre recordatorios de llegada para esa severidad */
	public Duration intervaloLlegada(Severidad severidad) {
		return switch (severidad) {
			case LEVE -> intervaloLeve;
			case MEDIA -> intervaloMedia;
			case GRAVE -> intervaloGrave;
		};
	}

	/** @return cantidad máxima de intervalos antes de agotar la paciencia y escalar */
	public int maximoRecordatorios(Severidad severidad) {
		return switch (severidad) {
			case LEVE -> maximoLeve;
			case MEDIA -> maximoMedia;
			case GRAVE -> maximoGrave;
		};
	}

}
