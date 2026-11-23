package cl.ucn.alertacuida.application.contacto;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.DatosContacto;
import cl.ucn.alertacuida.application.contacto.modelo.DatosPersona;
import cl.ucn.alertacuida.application.contacto.modelo.ListaContactos;
import cl.ucn.alertacuida.application.contacto.modelo.Persona;
import cl.ucn.alertacuida.application.contacto.modelo.TipoContacto;
import cl.ucn.alertacuida.application.contacto.modelo.Vinculo;
import cl.ucn.alertacuida.application.contacto.puerto.AvisoCambioContactos;
import cl.ucn.alertacuida.application.contacto.puerto.Contactos;
import cl.ucn.alertacuida.application.contacto.puerto.HashMedioContacto;
import cl.ucn.alertacuida.application.paciente.puerto.Pacientes;
import cl.ucn.alertacuida.domain.contacto.Cobertura;
import cl.ucn.alertacuida.domain.contacto.Correo;
import cl.ucn.alertacuida.domain.contacto.Horario;
import cl.ucn.alertacuida.domain.contacto.NumeroWhatsapp;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lista de contactos de un paciente: agregar, editar, desactivar, reactivar, ordenar y registrar
 * el opt-in de WhatsApp. Como la edición del paciente, se permite también con pacientes dados de baja.
 *
 * <p>Una persona se guarda una sola vez: si el WhatsApp o el correo ya son de alguien registrado,
 * se usa esa persona, y cambiar sus datos los cambia en todos sus pacientes. El opt-in es de la
 * persona (Meta lo registra por número), así que se pierde si cambia su número.
 *
 * <p>El SAMU y los números de emergencia no pueden ser contactos: el mensaje de una alerta grave
 * ya pide llamar al 131.
 */
public class ContactosPaciente {

	/** Largo máximo del nombre de un contacto. */
	static final int LARGO_MAXIMO_NOMBRE = 120;
	/** Largo máximo del parentesco. */
	static final int LARGO_MAXIMO_PARENTESCO = 60;

	private static final Pattern SAMU = Pattern.compile("\\bsamu\\b", Pattern.CASE_INSENSITIVE);

	private final Contactos contactos;
	private final Pacientes pacientes;
	private final HashMedioContacto hash;
	private final RegistroAuditoria auditoria;
	private final Transacciones transacciones;
	private final AvisoCambioContactos avisoCambio;
	private final Clock reloj;

	/**
	 * Recibe los puertos que usa; los arma ConfiguracionPerfilYContactos.
	 *
	 * @param avisoCambio se le informa cada cambio de la lista, después de guardarlo
	 */
	public ContactosPaciente(Contactos contactos, Pacientes pacientes, HashMedioContacto hash,
			RegistroAuditoria auditoria, Transacciones transacciones, AvisoCambioContactos avisoCambio,
			Clock reloj) {
		this.contactos = contactos;
		this.pacientes = pacientes;
		this.hash = hash;
		this.auditoria = auditoria;
		this.transacciones = transacciones;
		this.avisoCambio = avisoCambio;
		this.reloj = reloj;
	}

	/** @return los tipos de contacto que se pueden elegir */
	public List<TipoContacto> tipos() {
		return contactos.tipos();
	}

	/**
	 * @return primero los activos por prioridad, luego los desactivados por nombre, con las horas
	 *         en que ningún contacto activo puede recibir avisos
	 * @throws NoEncontrado si el paciente no existe
	 */
	public ListaContactos listar(UUID idPaciente) {
		exigirPaciente(idPaciente);
		List<ContactoDelPaciente> todos = contactos.delPaciente(idPaciente);
		Stream<ContactoDelPaciente> activos = todos.stream().filter(ContactoDelPaciente::activo)
				.sorted(Comparator.comparingInt(ContactoDelPaciente::prioridad));
		Stream<ContactoDelPaciente> inactivos = todos.stream().filter(contacto -> !contacto.activo())
				.sorted(Comparator.comparing(contacto -> contacto.persona().nombre(), String.CASE_INSENSITIVE_ORDER));
		List<Horario> disponibles = todos.stream().filter(ContactoDelPaciente::disponible)
				.map(contacto -> contacto.vinculo().horario())
				.toList();
		return new ListaContactos(Stream.concat(activos, inactivos).toList(), Cobertura.sinCubrir(disponibles));
	}

	/**
	 * Agrega un contacto al final de la lista. Si su WhatsApp o su correo ya son de una persona
	 * registrada, se vincula esa persona: se actualiza su nombre y se completa el medio que le faltaba.
	 *
	 * @param datos     lo ingresado por el equipo
	 * @param idUsuario quien lo agrega, para la auditoría
	 * @param ip        desde dónde llegó la petición, para la auditoría
	 * @return el identificador del vínculo (el que conoce el reloj)
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si un dato no cumple las reglas (incluido ingresar al SAMU)
	 * @throws Conflicto          si la persona ya es contacto del paciente, o el WhatsApp y el correo
	 *                            son de personas distintas
	 */
	public UUID agregar(UUID idPaciente, DatosContacto datos, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		DatosPersona persona = validarPersona(datos);
		Vinculo vinculo = validarVinculo(datos);
		Optional<Persona> existente = personaExistente(persona, null);
		List<ContactoDelPaciente> lista = contactos.delPaciente(idPaciente);
		if (existente.isPresent()) {
			UUID idPersona = existente.get().id();
			lista.stream().filter(contacto -> contacto.persona().id().equals(idPersona)).findFirst()
					.ifPresent(contacto -> {
						throw new Conflicto(contacto.activo()
								? "Esa persona ya es contacto del usuario"
								: "Esa persona es un contacto desactivado del usuario: reactívalo");
					});
		}
		int prioridad = lista.stream().filter(ContactoDelPaciente::activo)
				.mapToInt(ContactoDelPaciente::prioridad).max().orElse(0) + 1;
		Instant ahora = reloj.instant();
		UUID creado = transacciones.ejecutar(() -> {
			UUID idPersona;
			if (existente.isPresent()) {
				idPersona = existente.get().id();
				contactos.actualizarPersona(idPersona, completar(existente.get(), persona));
				auditoria.registrar(idUsuario, "MODIFICAR", "CONTACTO", idPersona.toString(), ip, ahora);
			} else {
				idPersona = contactos.crearPersona(persona);
				auditoria.registrar(idUsuario, "CREAR", "CONTACTO", idPersona.toString(), ip, ahora);
			}
			if (vinculo.cuidadorPrincipal()) {
				contactos.quitarCuidadorPrincipal(idPaciente);
			}
			UUID id = contactos.vincular(idPaciente, idPersona, vinculo, prioridad);
			auditoria.registrar(idUsuario, "CREAR", "CONTACTO_PACIENTE", id.toString(), ip, ahora);
			return id;
		});
		avisoCambio.listaCambiada(idPaciente);
		return creado;
	}

	/**
	 * Reemplaza los datos de un contacto. Nombre, WhatsApp y correo son de la persona y cambian en
	 * todos sus pacientes; si cambia el WhatsApp, el opt-in se pierde (era del número anterior).
	 *
	 * @param idContactoPaciente el vínculo a editar
	 * @throws NoEncontrado       si el paciente no existe, o el contacto no existe o es de otro paciente
	 * @throws SolicitudRechazada si un dato no cumple las reglas
	 * @throws Conflicto          si el WhatsApp o el correo ya son de otra persona
	 */
	public void editar(UUID idPaciente, UUID idContactoPaciente, DatosContacto datos, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		ContactoDelPaciente actual = buscar(idPaciente, idContactoPaciente);
		DatosPersona persona = validarPersona(datos);
		Vinculo vinculo = validarVinculo(datos);
		if (vinculo.cuidadorPrincipal() && !actual.activo()) {
			throw new SolicitudRechazada("Un contacto desactivado no puede ser el cuidador principal");
		}
		UUID idPersona = actual.persona().id();
		personaExistente(persona, idPersona);
		boolean cambiaWhatsapp = !Objects.equals(actual.persona().whatsapp(), persona.whatsapp());
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			contactos.actualizarPersona(idPersona, persona);
			if (cambiaWhatsapp && actual.persona().fechaOptIn() != null) {
				contactos.guardarOptIn(idPersona, null, null);
			}
			auditoria.registrar(idUsuario, "MODIFICAR", "CONTACTO", idPersona.toString(), ip, ahora);
			if (vinculo.cuidadorPrincipal() && !actual.vinculo().cuidadorPrincipal()) {
				contactos.quitarCuidadorPrincipal(idPaciente);
			}
			contactos.actualizarVinculo(idContactoPaciente, vinculo);
			auditoria.registrar(idUsuario, "MODIFICAR", "CONTACTO_PACIENTE", idContactoPaciente.toString(), ip,
					ahora);
			return null;
		});
		avisoCambio.listaCambiada(idPaciente);
	}

	/**
	 * Registra que la persona aceptó (o revocó) recibir WhatsApp, según lo informado al equipo.
	 * Como el opt-in es de la persona, vale para todos sus pacientes.
	 *
	 * @param acepta true si aceptó; false si lo revocó
	 * @throws NoEncontrado       si el paciente o el contacto no existen
	 * @throws SolicitudRechazada si el contacto no tiene WhatsApp
	 */
	public void registrarOptIn(UUID idPaciente, UUID idContactoPaciente, boolean acepta, UUID idUsuario,
			String ip) {
		exigirPaciente(idPaciente);
		Persona persona = buscar(idPaciente, idContactoPaciente).persona();
		if (persona.whatsapp() == null) {
			throw new SolicitudRechazada("El contacto no tiene WhatsApp");
		}
		if (acepta == persona.optInVigente()) {
			return;
		}
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			if (acepta) {
				contactos.guardarOptIn(persona.id(), ahora, null);
			} else {
				contactos.guardarOptIn(persona.id(), persona.fechaOptIn(), ahora);
			}
			auditoria.registrar(idUsuario, "MODIFICAR", "CONTACTO", persona.id().toString(), ip, ahora);
			return null;
		});
		avisoCambio.listaCambiada(idPaciente);
	}

	/**
	 * Saca al contacto de la lista sin borrarlo (sus avisos quedan en el historial). Los demás
	 * suben de prioridad para no dejar huecos.
	 *
	 * @throws NoEncontrado si el paciente o el contacto no existen
	 */
	public void desactivar(UUID idPaciente, UUID idContactoPaciente, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		ContactoDelPaciente contacto = buscar(idPaciente, idContactoPaciente);
		if (!contacto.activo()) {
			return;
		}
		List<UUID> restantes = activosPorPrioridad(idPaciente).stream()
				.filter(id -> !id.equals(idContactoPaciente))
				.toList();
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			contactos.desactivar(idContactoPaciente);
			contactos.reordenar(idPaciente, restantes);
			auditoria.registrar(idUsuario, "MODIFICAR", "CONTACTO_PACIENTE", idContactoPaciente.toString(), ip,
					ahora);
			return null;
		});
		avisoCambio.listaCambiada(idPaciente);
	}

	/**
	 * Vuelve a poner al contacto en la lista, al final.
	 *
	 * @throws NoEncontrado si el paciente o el contacto no existen
	 */
	public void reactivar(UUID idPaciente, UUID idContactoPaciente, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		if (buscar(idPaciente, idContactoPaciente).activo()) {
			return;
		}
		int prioridad = activosPorPrioridad(idPaciente).size() + 1;
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			contactos.reactivar(idContactoPaciente, prioridad);
			auditoria.registrar(idUsuario, "MODIFICAR", "CONTACTO_PACIENTE", idContactoPaciente.toString(), ip,
					ahora);
			return null;
		});
		avisoCambio.listaCambiada(idPaciente);
	}

	/**
	 * Cambia el orden en que el reloj avisa a los contactos.
	 *
	 * @param orden todos los contactos activos del paciente, del primero al último
	 * @throws NoEncontrado       si el paciente no existe
	 * @throws SolicitudRechazada si falta o sobra algún contacto activo, o se repite alguno
	 */
	public void reordenar(UUID idPaciente, List<UUID> orden, UUID idUsuario, String ip) {
		exigirPaciente(idPaciente);
		List<UUID> actual = activosPorPrioridad(idPaciente);
		if (orden == null || orden.size() != actual.size() || !new HashSet<>(orden).equals(new HashSet<>(actual))) {
			throw new SolicitudRechazada("El orden debe incluir una vez a cada contacto activo");
		}
		if (orden.equals(actual)) {
			return;
		}
		Instant ahora = reloj.instant();
		transacciones.ejecutar(() -> {
			contactos.reordenar(idPaciente, orden);
			for (int i = 0; i < orden.size(); i++) {
				if (!orden.get(i).equals(actual.get(i))) {
					auditoria.registrar(idUsuario, "MODIFICAR", "CONTACTO_PACIENTE", orden.get(i).toString(), ip,
							ahora);
				}
			}
			return null;
		});
		avisoCambio.listaCambiada(idPaciente);
	}

	/** @return los vínculos activos del paciente, por prioridad */
	private List<UUID> activosPorPrioridad(UUID idPaciente) {
		return contactos.delPaciente(idPaciente).stream()
				.filter(ContactoDelPaciente::activo)
				.sorted(Comparator.comparingInt(ContactoDelPaciente::prioridad))
				.map(ContactoDelPaciente::id)
				.toList();
	}

	/**
	 * Valida y normaliza nombre, WhatsApp y correo, y calcula sus hash.
	 *
	 * @throws SolicitudRechazada si falta el nombre o ambos medios, alguno no es válido, o es el SAMU
	 */
	private DatosPersona validarPersona(DatosContacto datos) {
		String nombre = datos.nombre() == null ? "" : datos.nombre().strip().replaceAll("\\s+", " ");
		if (nombre.isEmpty()) {
			throw new SolicitudRechazada("El nombre es obligatorio");
		}
		if (nombre.length() > LARGO_MAXIMO_NOMBRE) {
			throw new SolicitudRechazada("El nombre no puede tener más de " + LARGO_MAXIMO_NOMBRE + " caracteres");
		}
		if (SAMU.matcher(nombre).find()) {
			throw new SolicitudRechazada("El SAMU no puede ser contacto automático: el mensaje de una alerta "
					+ "grave ya pide llamar al 131");
		}
		boolean conWhatsapp = datos.whatsapp() != null && !datos.whatsapp().isBlank();
		boolean conCorreo = datos.correo() != null && !datos.correo().isBlank();
		if (!conWhatsapp && !conCorreo) {
			throw new SolicitudRechazada("Ingresa un WhatsApp o un correo");
		}
		try {
			NumeroWhatsapp whatsapp = conWhatsapp ? NumeroWhatsapp.de(datos.whatsapp()) : null;
			Correo correo = conCorreo ? Correo.de(datos.correo()) : null;
			return new DatosPersona(nombre,
					whatsapp == null ? null : whatsapp.valor(), whatsapp == null ? null : hash.de(whatsapp),
					correo == null ? null : correo.valor(), correo == null ? null : hash.de(correo));
		} catch (IllegalArgumentException e) {
			throw new SolicitudRechazada(e.getMessage());
		}
	}

	/**
	 * Valida tipo, parentesco y horario.
	 *
	 * @throws SolicitudRechazada si el tipo no existe, hay parentesco sin ser familia, o el horario no sirve
	 */
	private Vinculo validarVinculo(DatosContacto datos) {
		boolean tipoValido = datos.tipo() != null
				&& contactos.tipos().stream().anyMatch(tipo -> tipo.codigo().equals(datos.tipo()));
		if (!tipoValido) {
			throw new SolicitudRechazada("Elige un tipo de contacto válido");
		}
		String parentesco = datos.parentesco() == null || datos.parentesco().isBlank()
				? null : datos.parentesco().strip();
		if (parentesco != null && !TipoContacto.FAMILIA.equals(datos.tipo())) {
			throw new SolicitudRechazada("El parentesco solo se indica si el contacto es familiar");
		}
		if (parentesco != null && parentesco.length() > LARGO_MAXIMO_PARENTESCO) {
			throw new SolicitudRechazada("El parentesco no puede tener más de " + LARGO_MAXIMO_PARENTESCO
					+ " caracteres");
		}
		try {
			return new Vinculo(datos.tipo(), parentesco, datos.cuidadorPrincipal(),
					Horario.de(datos.horaInicio(), datos.horaFin()));
		} catch (IllegalArgumentException e) {
			throw new SolicitudRechazada(e.getMessage());
		}
	}

	/**
	 * @param excepto la persona que se está editando, o vacío al agregar
	 * @return la persona que ya tiene ese WhatsApp o correo (al agregar); vacío si no hay
	 * @throws Conflicto si el WhatsApp y el correo son de personas distintas, o (al editar) alguno es
	 *                   de otra persona
	 */
	private Optional<Persona> personaExistente(DatosPersona datos, UUID excepto) {
		Optional<Persona> porWhatsapp = datos.whatsappHash() == null
				? Optional.empty() : contactos.personaPorWhatsapp(datos.whatsappHash());
		Optional<Persona> porCorreo = datos.correoHash() == null
				? Optional.empty() : contactos.personaPorCorreo(datos.correoHash());
		if (excepto != null) {
			porWhatsapp.filter(persona -> !persona.id().equals(excepto)).ifPresent(persona -> {
				throw new Conflicto("Ese WhatsApp ya es de otro contacto registrado: " + persona.nombre());
			});
			porCorreo.filter(persona -> !persona.id().equals(excepto)).ifPresent(persona -> {
				throw new Conflicto("Ese correo ya es de otro contacto registrado: " + persona.nombre());
			});
			return Optional.empty();
		}
		if (porWhatsapp.isPresent() && porCorreo.isPresent() && !porWhatsapp.get().id().equals(porCorreo.get().id())) {
			throw new Conflicto("El WhatsApp es de " + porWhatsapp.get().nombre() + " y el correo de "
					+ porCorreo.get().nombre() + ": revisa los datos");
		}
		Optional<Persona> encontrada = porWhatsapp.or(() -> porCorreo);
		encontrada.ifPresent(persona -> {
			if (persona.whatsapp() != null && datos.whatsapp() != null
					&& !persona.whatsapp().equals(datos.whatsapp())) {
				throw new Conflicto("Ese correo ya es de " + persona.nombre() + ", que tiene otro WhatsApp");
			}
			if (persona.correo() != null && datos.correo() != null && !persona.correo().equals(datos.correo())) {
				throw new Conflicto("Ese WhatsApp ya es de " + persona.nombre() + ", que tiene otro correo");
			}
		});
		return encontrada;
	}

	/** @return los datos de la persona existente con el nombre nuevo y el medio que le faltaba */
	private DatosPersona completar(Persona existente, DatosPersona nuevos) {
		String whatsapp = existente.whatsapp() != null ? existente.whatsapp() : nuevos.whatsapp();
		String correo = existente.correo() != null ? existente.correo() : nuevos.correo();
		return new DatosPersona(nuevos.nombre(),
				whatsapp, whatsapp == null ? null : hash.de(NumeroWhatsapp.de(whatsapp)),
				correo, correo == null ? null : hash.de(Correo.de(correo)));
	}

	/** @throws NoEncontrado si el contacto no existe o es de otro paciente */
	private ContactoDelPaciente buscar(UUID idPaciente, UUID idContactoPaciente) {
		return contactos.delPaciente(idPaciente).stream()
				.filter(contacto -> contacto.id().equals(idContactoPaciente))
				.findFirst()
				.orElseThrow(() -> new NoEncontrado("El usuario no tiene ese contacto"));
	}

	/** @throws NoEncontrado si el paciente no existe */
	private void exigirPaciente(UUID idPaciente) {
		pacientes.buscar(idPaciente).orElseThrow(() -> new NoEncontrado("No existe ese usuario"));
	}

}
