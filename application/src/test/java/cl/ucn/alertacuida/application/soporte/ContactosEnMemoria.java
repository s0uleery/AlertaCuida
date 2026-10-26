package cl.ucn.alertacuida.application.soporte;

import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.DatosPersona;
import cl.ucn.alertacuida.application.contacto.modelo.Persona;
import cl.ucn.alertacuida.application.contacto.modelo.TipoContacto;
import cl.ucn.alertacuida.application.contacto.modelo.Vinculo;
import cl.ucn.alertacuida.application.contacto.puerto.Contactos;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble de {@link Contactos} para las pruebas unitarias: guarda en mapas. Revisa, como la base,
 * que la prioridad y el cuidador principal no se repitan entre los activos.
 */
public final class ContactosEnMemoria implements Contactos {

	/** Una persona con sus hash. */
	private record FilaPersona(Persona persona, String whatsappHash, String correoHash) {
	}

	/** Un vínculo con un paciente. */
	private record FilaVinculo(UUID idPaciente, UUID idContacto, Vinculo vinculo, int prioridad, boolean activo) {
	}

	private final Map<UUID, FilaPersona> personas = new LinkedHashMap<>();
	private final Map<UUID, FilaVinculo> vinculos = new LinkedHashMap<>();

	@Override
	public List<TipoContacto> tipos() {
		return List.of(new TipoContacto("FAMILIA", "Familia"), new TipoContacto("VECINO", "Vecino o vecina"),
				new TipoContacto("SEGURIDAD_CIUDADANA", "Seguridad Ciudadana"));
	}

	@Override
	public List<ContactoDelPaciente> delPaciente(UUID idPaciente) {
		return vinculos.entrySet().stream()
				.filter(fila -> fila.getValue().idPaciente().equals(idPaciente))
				.map(fila -> {
					FilaVinculo vinculo = fila.getValue();
					int otros = (int) vinculos.values().stream()
							.filter(otro -> otro.idContacto().equals(vinculo.idContacto()) && otro.activo()
									&& !otro.idPaciente().equals(idPaciente))
							.count();
					return new ContactoDelPaciente(fila.getKey(), personas.get(vinculo.idContacto()).persona(),
							vinculo.vinculo(), vinculo.prioridad(), vinculo.activo(), otros);
				})
				.toList();
	}

	@Override
	public Optional<Persona> personaPorWhatsapp(String whatsappHash) {
		return personas.values().stream().filter(fila -> whatsappHash.equals(fila.whatsappHash()))
				.map(FilaPersona::persona).findFirst();
	}

	@Override
	public Optional<Persona> personaPorCorreo(String correoHash) {
		return personas.values().stream().filter(fila -> correoHash.equals(fila.correoHash()))
				.map(FilaPersona::persona).findFirst();
	}

	@Override
	public UUID crearPersona(DatosPersona datos) {
		UUID id = UUID.randomUUID();
		guardar(id, datos, null, null);
		return id;
	}

	@Override
	public void actualizarPersona(UUID idContacto, DatosPersona datos) {
		Persona actual = personas.get(idContacto).persona();
		guardar(idContacto, datos, actual.fechaOptIn(), actual.fechaRevocacion());
	}

	@Override
	public void guardarOptIn(UUID idContacto, Instant fechaOptIn, Instant fechaRevocacion) {
		FilaPersona fila = personas.get(idContacto);
		Persona p = fila.persona();
		personas.put(idContacto, new FilaPersona(new Persona(idContacto, p.nombre(), p.whatsapp(), p.correo(),
				fechaOptIn, fechaRevocacion), fila.whatsappHash(), fila.correoHash()));
	}

	@Override
	public UUID vincular(UUID idPaciente, UUID idContacto, Vinculo vinculo, int prioridad) {
		UUID id = UUID.randomUUID();
		guardarVinculo(id, new FilaVinculo(idPaciente, idContacto, vinculo, prioridad, true));
		return id;
	}

	@Override
	public void actualizarVinculo(UUID idContactoPaciente, Vinculo vinculo) {
		FilaVinculo fila = vinculos.get(idContactoPaciente);
		guardarVinculo(idContactoPaciente, new FilaVinculo(fila.idPaciente(), fila.idContacto(), vinculo,
				fila.prioridad(), fila.activo()));
	}

	@Override
	public void quitarCuidadorPrincipal(UUID idPaciente) {
		vinculos.replaceAll((id, fila) -> fila.idPaciente().equals(idPaciente)
				? new FilaVinculo(fila.idPaciente(), fila.idContacto(), sinCuidador(fila.vinculo()), fila.prioridad(),
						fila.activo())
				: fila);
	}

	@Override
	public void desactivar(UUID idContactoPaciente) {
		FilaVinculo fila = vinculos.get(idContactoPaciente);
		vinculos.put(idContactoPaciente, new FilaVinculo(fila.idPaciente(), fila.idContacto(),
				sinCuidador(fila.vinculo()), fila.prioridad(), false));
	}

	@Override
	public void reactivar(UUID idContactoPaciente, int prioridad) {
		FilaVinculo fila = vinculos.get(idContactoPaciente);
		guardarVinculo(idContactoPaciente, new FilaVinculo(fila.idPaciente(), fila.idContacto(), fila.vinculo(),
				prioridad, true));
	}

	@Override
	public void reordenar(UUID idPaciente, List<UUID> orden) {
		for (int i = 0; i < orden.size(); i++) {
			FilaVinculo fila = vinculos.get(orden.get(i));
			vinculos.put(orden.get(i), new FilaVinculo(fila.idPaciente(), fila.idContacto(), fila.vinculo(), i + 1,
					fila.activo()));
		}
	}

	/** Guarda la persona revisando que sus hash sean únicos, como la base. */
	private void guardar(UUID id, DatosPersona datos, Instant optIn, Instant revocacion) {
		boolean repetido = personas.entrySet().stream().anyMatch(fila -> !fila.getKey().equals(id)
				&& ((datos.whatsappHash() != null && datos.whatsappHash().equals(fila.getValue().whatsappHash()))
						|| (datos.correoHash() != null && datos.correoHash().equals(fila.getValue().correoHash()))));
		if (repetido || (datos.whatsapp() == null) != (datos.whatsappHash() == null)
				|| (datos.correo() == null) != (datos.correoHash() == null)) {
			throw new IllegalStateException("La base rechazaría esta persona");
		}
		personas.put(id, new FilaPersona(new Persona(id, datos.nombre(), datos.whatsapp(), datos.correo(), optIn,
				revocacion), datos.whatsappHash(), datos.correoHash()));
	}

	/** Guarda el vínculo revisando, como los índices únicos, prioridad y cuidador entre los activos. */
	private void guardarVinculo(UUID id, FilaVinculo nueva) {
		boolean choca = nueva.activo() && vinculos.entrySet().stream().anyMatch(fila -> !fila.getKey().equals(id)
				&& fila.getValue().activo() && fila.getValue().idPaciente().equals(nueva.idPaciente())
				&& (fila.getValue().prioridad() == nueva.prioridad()
						|| (nueva.vinculo().cuidadorPrincipal() && fila.getValue().vinculo().cuidadorPrincipal())));
		boolean repetido = vinculos.entrySet().stream().anyMatch(fila -> !fila.getKey().equals(id)
				&& fila.getValue().idPaciente().equals(nueva.idPaciente())
				&& Objects.equals(fila.getValue().idContacto(), nueva.idContacto()));
		if (choca || repetido) {
			throw new IllegalStateException("La base rechazaría este vínculo");
		}
		vinculos.put(id, nueva);
	}

	/** @return el mismo vínculo, sin el rol de cuidador principal */
	private static Vinculo sinCuidador(Vinculo vinculo) {
		return new Vinculo(vinculo.tipo(), vinculo.parentesco(), false, vinculo.horario());
	}

}
