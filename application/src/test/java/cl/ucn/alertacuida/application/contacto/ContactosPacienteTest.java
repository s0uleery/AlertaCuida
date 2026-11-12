package cl.ucn.alertacuida.application.contacto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.ucn.alertacuida.application.auditoria.puerto.RegistroAuditoria;
import cl.ucn.alertacuida.application.comun.error.Conflicto;
import cl.ucn.alertacuida.application.comun.error.NoEncontrado;
import cl.ucn.alertacuida.application.comun.error.SolicitudRechazada;
import cl.ucn.alertacuida.application.comun.puerto.Transacciones;
import cl.ucn.alertacuida.application.contacto.modelo.ContactoDelPaciente;
import cl.ucn.alertacuida.application.contacto.modelo.DatosContacto;
import cl.ucn.alertacuida.application.contacto.puerto.HashMedioContacto;
import cl.ucn.alertacuida.application.paciente.modelo.DatosPaciente;
import cl.ucn.alertacuida.application.soporte.ContactosEnMemoria;
import cl.ucn.alertacuida.application.soporte.PacientesEnMemoria;
import cl.ucn.alertacuida.domain.contacto.Canal;
import cl.ucn.alertacuida.domain.contacto.Cobertura;
import cl.ucn.alertacuida.domain.contacto.Correo;
import cl.ucn.alertacuida.domain.contacto.NumeroWhatsapp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Lista de contactos de un paciente, sin base de datos. */
class ContactosPacienteTest {

	private static final UUID COORDINADOR = UUID.randomUUID();

	private final PacientesEnMemoria pacientes = new PacientesEnMemoria();
	private final ContactosEnMemoria guardados = new ContactosEnMemoria();
	private final List<String> auditoria = new ArrayList<>();
	private ContactosPaciente contactos;
	private UUID maria;
	private UUID pedro;

	/** El caso de uso con dobles en memoria y dos pacientes ya registrados. */
	@BeforeEach
	void preparar() {
		Clock reloj = Clock.fixed(Instant.parse("2026-10-25T15:00:00Z"), ZoneOffset.UTC);
		RegistroAuditoria registro = (usuario, accion, entidad, id, ip, fecha) -> auditoria.add(accion + " " + entidad);
		Transacciones enTransaccion = Supplier::get;
		HashMedioContacto hash = new HashMedioContacto() {
			@Override
			public String de(NumeroWhatsapp numero) {
				return "w:" + numero.valor();
			}

			@Override
			public String de(Correo correo) {
				return "c:" + correo.valor();
			}
		};
		contactos = new ContactosPaciente(guardados, pacientes, hash, registro, enTransaccion, id -> { }, reloj);
		maria = pacientes.crear(new DatosPaciente("12345678-5", "María", "Rojas", null, null, null, null, null, null,
				LocalDate.of(2026, 1, 10)), "h1");
		pedro = pacientes.crear(new DatosPaciente("9999999-3", "Pedro", "Soto", null, null, null, null, null, null,
				LocalDate.of(2026, 1, 10)), "h2");
	}

	/** Cada contacto nuevo va al final; la lista normaliza número y correo. */
	@Test
	void agregaAlFinalConLosDatosNormalizados() {
		UUID ana = contactos.agregar(maria, familiar("Ana Rojas", "9 1234 5678", null), COORDINADOR, "ip");
		UUID luis = contactos.agregar(maria, vecino("Luis", null, " Luis@Mail.CL"), COORDINADOR, "ip");

		List<ContactoDelPaciente> lista = contactos.listar(maria).contactos();
		assertThat(lista).extracting(ContactoDelPaciente::id).containsExactly(ana, luis);
		assertThat(lista).extracting(ContactoDelPaciente::prioridad).containsExactly(1, 2);
		assertThat(lista.getFirst().persona().whatsapp()).isEqualTo("+56912345678");
		assertThat(lista.getLast().persona().correo()).isEqualTo("luis@mail.cl");
		assertThat(auditoria).containsExactly("CREAR CONTACTO", "CREAR CONTACTO_PACIENTE", "CREAR CONTACTO",
				"CREAR CONTACTO_PACIENTE");
	}

	/** El SAMU, por nombre o por número, no puede ser contacto. */
	@Test
	void elSamuNoPuedeSerContacto() {
		assertThatThrownBy(() -> contactos.agregar(maria, vecino("SAMU Antofagasta", null, "samu@salud.cl"),
				COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class)
				.hasMessageContaining("131");
		assertThatThrownBy(() -> contactos.agregar(maria, vecino("Emergencias", "131", null), COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThat(contactos.listar(maria).contactos()).isEmpty();
	}

	/** Sin medio de contacto, con un tipo inexistente, parentesco sin ser familia o media hora, se rechaza. */
	@Test
	void rechazaDatosQueNoCumplenLasReglas() {
		assertThatThrownBy(() -> contactos.agregar(maria, vecino("Luis", null, null), COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> contactos.agregar(maria, new DatosContacto("Luis", null, "l@mail.cl", "AMIGO", null,
				false, null, null), COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> contactos.agregar(maria, new DatosContacto("Luis", null, "l@mail.cl", "VECINO",
				"hijo", false, null, null), COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> contactos.agregar(maria, new DatosContacto("Luis", null, "l@mail.cl", "VECINO",
				null, false, LocalTime.of(8, 0), null), COORDINADOR, "ip")).isInstanceOf(SolicitudRechazada.class);
	}

	/** Una persona ya registrada en otro paciente se reutiliza; en el mismo paciente no se repite. */
	@Test
	void reutilizaLaPersonaDeOtroPacienteSinRepetirlaEnElMismo() {
		contactos.agregar(maria, seguridad("Seguridad Ciudadana", "seguridad@muni.cl"), COORDINADOR, "ip");
		contactos.agregar(pedro, seguridad("Seguridad Ciudadana Antofagasta", "SEGURIDAD@muni.cl"), COORDINADOR,
				"ip");

		ContactoDelPaciente deMaria = contactos.listar(maria).contactos().getFirst();
		ContactoDelPaciente dePedro = contactos.listar(pedro).contactos().getFirst();
		assertThat(dePedro.persona().id()).isEqualTo(deMaria.persona().id());
		assertThat(deMaria.persona().nombre()).isEqualTo("Seguridad Ciudadana Antofagasta");
		assertThat(deMaria.otrosPacientes()).isEqualTo(1);
		assertThatThrownBy(() -> contactos.agregar(maria, seguridad("Seguridad", "seguridad@muni.cl"), COORDINADOR,
				"ip")).isInstanceOf(Conflicto.class);
	}

	/** Un WhatsApp de una persona y el correo de otra no se mezclan. */
	@Test
	void unWhatsappYUnCorreoDePersonasDistintasChocan() {
		contactos.agregar(maria, familiar("Ana", "912345678", null), COORDINADOR, "ip");
		contactos.agregar(maria, vecino("Luis", null, "luis@mail.cl"), COORDINADOR, "ip");

		assertThatThrownBy(() -> contactos.agregar(pedro, vecino("Otro", "912345678", "luis@mail.cl"), COORDINADOR,
				"ip")).isInstanceOf(Conflicto.class);
	}

	/** Sin opt-in un contacto solo con WhatsApp no cuenta como disponible; con opt-in sí, y revocarlo lo quita. */
	@Test
	void elOptInDecideSiSePuedeUsarElWhatsapp() {
		UUID ana = contactos.agregar(maria, familiar("Ana", "912345678", null), COORDINADOR, "ip");
		Cobertura.Tramo diaCompleto = new Cobertura.Tramo(LocalTime.MIDNIGHT, LocalTime.MIDNIGHT);

		assertThat(contactos.listar(maria).sinCobertura()).containsExactly(diaCompleto);
		contactos.registrarOptIn(maria, ana, true, COORDINADOR, "ip");
		assertThat(contactos.listar(maria).contactos().getFirst().canales()).containsExactly(Canal.WHATSAPP);
		assertThat(contactos.listar(maria).sinCobertura()).isEmpty();
		contactos.registrarOptIn(maria, ana, false, COORDINADOR, "ip");
		assertThat(contactos.listar(maria).contactos().getFirst().canales()).isEmpty();
		assertThat(contactos.listar(maria).contactos().getFirst().persona().fechaRevocacion()).isNotNull();
	}

	/** El opt-in solo se registra en contactos con WhatsApp. */
	@Test
	void elOptInNecesitaWhatsapp() {
		UUID luis = contactos.agregar(maria, vecino("Luis", null, "luis@mail.cl"), COORDINADOR, "ip");

		assertThatThrownBy(() -> contactos.registrarOptIn(maria, luis, true, COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
	}

	/** Cambiar el número pierde el opt-in, que era del número anterior. */
	@Test
	void cambiarElWhatsappPierdeElOptIn() {
		UUID ana = contactos.agregar(maria, familiar("Ana", "912345678", null), COORDINADOR, "ip");
		contactos.registrarOptIn(maria, ana, true, COORDINADOR, "ip");

		contactos.editar(maria, ana, familiar("Ana", "+56 9 1234 5678", null), COORDINADOR, "ip");
		assertThat(contactos.listar(maria).contactos().getFirst().persona().optInVigente()).isTrue();
		contactos.editar(maria, ana, familiar("Ana", "987654321", null), COORDINADOR, "ip");
		assertThat(contactos.listar(maria).contactos().getFirst().persona().optInVigente()).isFalse();
	}

	/** Editar con el número de otra persona choca; editar un contacto de otro paciente no lo encuentra. */
	@Test
	void editarNoTomaElNumeroDeOtraPersonaNiContactosAjenos() {
		UUID ana = contactos.agregar(maria, familiar("Ana", "912345678", null), COORDINADOR, "ip");
		UUID luis = contactos.agregar(maria, vecino("Luis", "987654321", null), COORDINADOR, "ip");

		assertThatThrownBy(() -> contactos.editar(maria, luis, vecino("Luis", "912345678", null), COORDINADOR, "ip"))
				.isInstanceOf(Conflicto.class);
		assertThatThrownBy(() -> contactos.editar(pedro, ana, familiar("Ana", "912345678", null), COORDINADOR, "ip"))
				.isInstanceOf(NoEncontrado.class);
	}

	/** Marcar a otro como cuidador principal se lo quita al anterior. */
	@Test
	void elCuidadorPrincipalEsUnoSolo() {
		contactos.agregar(maria, cuidador("Ana", "912345678"), COORDINADOR, "ip");
		contactos.agregar(maria, cuidador("Rosa", "987654321"), COORDINADOR, "ip");

		assertThat(contactos.listar(maria).contactos())
				.extracting(contacto -> contacto.vinculo().cuidadorPrincipal())
				.containsExactly(false, true);
	}

	/** Desactivar sube a los siguientes sin dejar huecos; reactivar lo deja al final. */
	@Test
	void desactivarSubeALosSiguientesYReactivarDejaAlFinal() {
		UUID ana = contactos.agregar(maria, familiar("Ana", "912345678", null), COORDINADOR, "ip");
		UUID luis = contactos.agregar(maria, vecino("Luis", null, "luis@mail.cl"), COORDINADOR, "ip");
		UUID rosa = contactos.agregar(maria, vecino("Rosa", null, "rosa@mail.cl"), COORDINADOR, "ip");

		contactos.desactivar(maria, ana, COORDINADOR, "ip");
		List<ContactoDelPaciente> lista = contactos.listar(maria).contactos();
		assertThat(lista).extracting(ContactoDelPaciente::id).containsExactly(luis, rosa, ana);
		assertThat(lista).extracting(ContactoDelPaciente::prioridad).startsWith(1, 2);
		assertThat(lista.getLast().activo()).isFalse();

		contactos.reactivar(maria, ana, COORDINADOR, "ip");
		assertThat(contactos.listar(maria).contactos()).extracting(ContactoDelPaciente::prioridad)
				.containsExactly(1, 2, 3);
		assertThat(contactos.listar(maria).contactos().getLast().id()).isEqualTo(ana);
	}

	/** Reordenar exige a todos los activos, una vez cada uno. */
	@Test
	void reordenarExigeATodosLosActivos() {
		UUID ana = contactos.agregar(maria, familiar("Ana", "912345678", null), COORDINADOR, "ip");
		UUID luis = contactos.agregar(maria, vecino("Luis", null, "luis@mail.cl"), COORDINADOR, "ip");

		assertThatThrownBy(() -> contactos.reordenar(maria, List.of(luis), COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		assertThatThrownBy(() -> contactos.reordenar(maria, List.of(luis, luis), COORDINADOR, "ip"))
				.isInstanceOf(SolicitudRechazada.class);
		contactos.reordenar(maria, List.of(luis, ana), COORDINADOR, "ip");

		assertThat(contactos.listar(maria).contactos()).extracting(ContactoDelPaciente::id).containsExactly(luis, ana);
	}

	/** Un contacto de 08:00 a 20:00 deja la noche sin cobertura. */
	@Test
	void informaLasHorasSinCobertura() {
		contactos.agregar(maria, new DatosContacto("Luis", null, "luis@mail.cl", "VECINO", null, false,
				LocalTime.of(8, 0), LocalTime.of(20, 0)), COORDINADOR, "ip");

		assertThat(contactos.listar(maria).sinCobertura())
				.containsExactly(new Cobertura.Tramo(LocalTime.of(20, 0), LocalTime.of(8, 0)));
	}

	/** @return un familiar sin horario (todo el día) */
	private static DatosContacto familiar(String nombre, String whatsapp, String correo) {
		return new DatosContacto(nombre, whatsapp, correo, "FAMILIA", "hija", false, null, null);
	}

	/** @return un familiar que es el cuidador principal */
	private static DatosContacto cuidador(String nombre, String whatsapp) {
		return new DatosContacto(nombre, whatsapp, null, "FAMILIA", null, true, null, null);
	}

	/** @return un vecino sin horario */
	private static DatosContacto vecino(String nombre, String whatsapp, String correo) {
		return new DatosContacto(nombre, whatsapp, correo, "VECINO", null, false, null, null);
	}

	/** @return Seguridad Ciudadana con correo */
	private static DatosContacto seguridad(String nombre, String correo) {
		return new DatosContacto(nombre, null, correo, "SEGURIDAD_CIUDADANA", null, false, null, null);
	}

}
