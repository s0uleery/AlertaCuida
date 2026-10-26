# Cronograma de desarrollo: plataforma de teleasistencia PRLAC (AlertaCuida), versión 7

Versión 7. Reemplaza a la v6 por el cambio de diseño del 15 de octubre de 2026: **el reloj tendrá LTE y vuelve a gestionar los contactos y el escalamiento**, como en el plan inicial. Los avisos siguen siendo por WhatsApp y correo, sin llamadas ni SMS: el reloj decide a quién avisar y cuándo, y el servidor envía los mensajes. Se mantienen de la v6: sin botón de pánico, sin presión arterial y servidor interno de la municipalidad. Lista ordenada de tareas de la plataforma, sin fechas, con estimación en horas-persona, una propuesta de sprints y los hitos del proyecto.

Archivos relacionados: `tecnologias_plataforma_prlac_v3.md` y el [modelo de datos](modelo-datos.md). El diagrama de secuencia v6 debe actualizarse a la v7 (LTE, escalamiento en el reloj, envío desde el servidor).

## Resumen

| Concepto | Valor |
|---|---|
| Tareas | 49 tareas activas y sus subtareas, en 6 fases (2 eliminadas respecto de la v5) |
| Esfuerzo total | 774 horas-persona (unas 1.006 con 30 % de margen) |
| Ya hecho | 162 horas al 25-10-2026 (0.1, 0.2, 0.3, casi toda 0.4, casi toda 1.1, 1.2, parte de 1.3, casi toda 1.4, 1.5, 3.2 y 3.3) |
| Capacidad del equipo | 60 a 90 horas por semana (3 personas, 20 a 30 h cada una) |
| Duración estimada | 7 sprints de 2 semanas, unas 14 semanas |
| Fuera de alcance | App del reloj (ver la sección correspondiente) |

## Qué cambió respecto de la v6

| Antes (v6, 9 de octubre) | Ahora (v7, 15 de octubre) |
|---|---|
| Reloj SM-L320 sin LTE, solo Wi-Fi. Internet por router 4G o por teléfono (hotspot o emparejado) | Reloj con LTE y SIM propia (modelo por confirmar). Wi-Fi opcional. Sin router ni teléfono intermediario |
| El servidor escala a los contactos (motor de escalamiento) | El reloj escala: elige el contacto por prioridad y horario, espera y pasa al siguiente. Le pide al servidor cada aviso |
| El servidor envía los WhatsApp por decisión propia | El servidor envía el WhatsApp o el correo que pide el reloj. La clave de Meta queda solo en el servidor |
| Contactos y plazos solo en el servidor | Contactos (sin números ni correos), plazo de espera, horario hábil y feriados viajan al reloj en la configuración |
| Temporizadores del escalamiento en la base del servidor | En el reloj. El servidor registra lo que el reloj informa |
| FCM "alerta confirmada" informa al reloj | FCM "alerta confirmada" además detiene el escalamiento del reloj |
| Zona segura de GPS | Eliminada, para cuidar la batería. El GPS solo ubica a la persona |
| Modelo de datos de 30 tablas | 22 tablas ([modelo de datos](modelo-datos.md)) |

Se mantienen de la v6: avisos por WhatsApp (con opt-in) y correo, sin llamadas ni SMS; sin botón SOS; sin presión arterial; servidor interno de la municipalidad. La tarea 2.10 se reduce (de 32 a 22 h) y cambian 0.4, 0.5, 1.5, 1.7, 1.9, 3.8 y las pruebas con el reloj real.

Respecto de la v5 (plan inicial): el reloj vuelve a escalar, pero por WhatsApp a través del servidor y no por llamadas ni SMS. Siguen eliminadas 1.8 (presión arterial) y la antigua 4.0 (Google Cloud).

## Alcance y decisiones tomadas

- La plataforma incluye: backend, base de datos, dashboard para el PRLAC, **envío y registro de los avisos que pide el reloj**, canal de WhatsApp, canal de correo, avisos al reloj por FCM, auditoría de alertas y vigilancia del silencio de datos.
- El reloj y la plataforma son dos sistemas aparte. Cada reloj tiene un ID único, vinculado a un ID de usuario.
- División del trabajo: el reloj decide el "qué", el "quién" y el "cuándo". Evalúa sus lecturas con los parámetros del paciente, detecta la emergencia, elige el contacto según prioridad y horario, espera la confirmación y pasa al siguiente. El servidor hace el "cómo": envía el WhatsApp o el correo que pide el reloj, recibe la respuesta del contacto, se la avisa al reloj y lo registra todo. Además, el servidor genera las alertas técnicas (silencio de datos, batería, reloj no puesto) y avisa por correo a los profesionales.
- Tipos de evento: caída con inmovilidad posterior, FC o SpO2 anormal en 2 lecturas consecutivas, tendencia anormal sobre el lote de lecturas y FC y SpO2 anormales a la vez. **No hay botón de pánico.**
- Ventana de cancelación: el reloj vibra 30 segundos antes de enviar la alerta; el paciente puede cancelarla.
- Escalamiento en el reloj: el reloj pide al servidor avisar al contacto de prioridad 1 (plantilla de WhatsApp con botones). Si no hay confirmación en el plazo configurado, consulta el estado de la alerta y pasa al de prioridad 2. Al terminar la lista, la repite una vez e informa `SIN_RESPUESTA`. El servidor avisa al reloj por FCM cuando un contacto confirma. Las alertas medias y graves avisan además por correo a los profesionales y quedan destacadas en el dashboard.
- Nadie llama automáticamente al SAMU. En alertas graves, el mensaje indica al contacto que llame al 131.
- Avisos al reloj por Firebase Cloud Messaging (FCM, gratuito), solo con identificadores, sin datos de salud.
- Sincronización: cada 2 horas (o cuando la batería baja de 15 %) el reloj envía su estado y las lecturas acumuladas. Guarda 14 días de datos locales.
- Mediciones de FC y SpO2 periódicas (cada 30 minutos) con el Samsung Health Sensor SDK. El acelerómetro es el único sensor continuo. Las lecturas de SpO2 fallidas se descartan.
- Gravedad en tres niveles: leve, media y grave. Destinatarios según la gravedad y el horario (Anexo B). Las alertas leves fuera del horario hábil no generan mensajes: quedan en el dashboard para el siguiente día hábil. Las medias y las graves avisan siempre, a los contactos disponibles a esa hora, así que cada paciente debe tener contactos disponibles a toda hora.
- Contactos compartidos o exclusivos: una misma persona o institución (por ejemplo, Seguridad Ciudadana) puede ser contacto de varios pacientes, con prioridad y horario distintos en cada uno; otras son contacto de un solo paciente.
- Horarios: todo se guarda en UTC y se evalúa en la zona `America/Santiago`.
- Costos: correo por SMTP institucional y FCM son gratuitos; WhatsApp se cobra por plantilla enviada (monto bajo para el volumen del piloto). El plan de datos LTE de cada reloj es un costo mensual fijo nuevo.
- Despliegue en el servidor interno de la municipalidad, con Docker. Solo la API del reloj y el webhook de WhatsApp quedan expuestos a internet; el dashboard y la base de datos quedan internos.
- La plataforma es independiente: sin conexión con los sistemas que ya usa el PRLAC.
- Meta del primer entregable (MVP): el flujo completo, listo para el piloto con usuarios reales.
- Tecnologías decididas: Java con Spring Boot, Maven, PostgreSQL, React con Tailwind.
- Equipo: 3 personas, nivel intermedio, trabajo repartido por especialidad, 20 a 30 horas semanales por integrante, sistema operativo Windows.
- Proyecto municipal: se prioriza el menor costo posible.
- Datos cifrados y en servidores en Chile (servidor municipal). En pruebas se usan solo datos inventados.
## Cómo leer este documento

- Horas-persona: horas de trabajo de una persona. Margen de error aproximado de ±30 %.
- Cada tarea tiene subtareas de 1 a 8 horas. Las horas de las subtareas suman exactamente las de la tarea.
- "Listo cuando" describe cómo saber que la tarea está terminada.
- "Depende de" indica qué tarea debe estar lista antes de comenzar.
- Las tareas marcadas "Nueva" o "Cambia" difieren de la v5. Las marcadas "Cambia en v7" se ajustaron el 15 de octubre de 2026 por el reloj con LTE.
- Marca cada subtarea con `[x]` al terminarla.
---

## Fase 0: decisiones y base (87 h)

### 0.1 Decidir tecnologías (8 h). Hecha.
Elegir las tecnologías de cada parte del sistema. Ya está hecha: Java con Spring Boot, Maven, PostgreSQL y React con Tailwind.

Depende de: —. Listo cuando: están elegidos backend, base de datos, dashboard, herramienta de construcción y protocolo.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 0.1.1 | Comparar opciones de backend (Spring Boot, NestJS, FastAPI) | 3 |
| [x] | 0.1.2 | Elegir base de datos | 2 |
| [x] | 0.1.3 | Elegir tecnología del dashboard y estilos | 2 |
| [x] | 0.1.4 | Elegir herramienta de construcción y protocolo de comunicación | 1 |

### 0.2 Definir dónde se aloja la plataforma (2 h). Hecha. Cambia.
Decidir dónde corre la plataforma durante el piloto. Decisión vigente: servidor interno de la municipalidad (reemplaza la decisión anterior de Google Cloud).

Depende de: —. Listo cuando: el lugar de alojamiento está decidido.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 0.2.1 | Comparar nube y servidor municipal (costo, datos en Chile, control) | 1 |
| [x] | 0.2.2 | Decidir el servidor interno de la municipalidad | 1 |

### 0.3 Repositorio y estructura del proyecto (12 h). Hecha.
Crear el repositorio de código y el esqueleto del proyecto Maven con su estructura en capas, las reglas de trabajo del equipo y la integración continua.

Depende de: 0.1. Listo cuando: cualquier integrante clona el repositorio, compila con una orden y las pruebas corren solas en cada envío de código.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 0.3.1 | Crear el repositorio (AlertaCuida), definir ramas (`main` y `develop`) y reglas de protección | 2 |
| [x] | 0.3.2 | Instalar herramientas en Windows (JDK, Maven, Git, IDE, Node.js) y anotar las versiones usadas | 2 |
| [x] | 0.3.3 | Crear el proyecto Maven con Spring Initializr y confirmar la estructura multi-módulo (domain, application, adapters, app) | 3 |
| [x] | 0.3.4 | Convenciones: estilo de código (Checkstyle), nombres, formato de mensajes de commit, plantilla de revisión de código y README | 2 |
| [x] | 0.3.5 | Prueba de arranque del backend con JUnit | 1 |
| [x] | 0.3.6 | GitHub Actions: compilar y correr pruebas en cada envío de código | 2 |

### 0.4 Modelo de datos y contrato de la API (20 h). Cambia en v7.
Definir qué datos guarda el sistema y escribir el contrato de la API entre el reloj y la plataforma, y el del webhook de WhatsApp. Es lo primero que necesita quien desarrolla el reloj.

Depende de: 0.1. Listo cuando: el modelo está dibujado y el contrato en OpenAPI (sincronización, alerta, cancelación por el paciente, solicitud de aviso a un contacto, estado de la alerta, configuración, webhook y avisos FCM) fue revisado con quien desarrolla el reloj.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 0.4.1 | Listar entidades y relaciones (paciente, usuario del sistema, reloj, perfil clínico, contacto con opt-in, medición, alerta, estado de alerta, mensaje enviado, sincronización, auditoría) y dibujar el modelo | 3 |
| [x] | 0.4.2 | Definir la separación entre identidad y telemetría, y las claves (UUID) | 2 |
| [x] | 0.4.3 | Definir el formato de la sincronización cada 2 h (estado del reloj, tipo de conexión, lecturas con hora original en UTC, `event_id`, contadores de eventos descartados) | 3 |
| [x] | 0.4.4 | Definir el formato de la alerta (tipo, gravedad, evidencia de lecturas), de la solicitud de aviso a un contacto, de la consulta de estado y del fin del escalamiento, y los estados de la alerta en el servidor | 3 |
| [x] | 0.4.5 | Definir el formato de la configuración del reloj: umbrales, reglas de tendencia, módulos activos, GPS sí o no, modo conservador, contactos (identificador, nombre para mostrar, prioridad, horario y canales, sin números ni correos), plazo de espera, horario hábil, feriados y versión | 3 |
| [x] | 0.4.6 | Definir los avisos por FCM (alerta confirmada, alerta resuelta, configuración nueva, simulacro), solo con identificadores | 2 |
| [x] | 0.4.7 | Escribir el contrato en OpenAPI, incluidos la solicitud de aviso, el estado de la alerta y el webhook de WhatsApp: endpoints, errores, autenticación | 3 |
| [ ] | 0.4.8 | Revisar el contrato con quien desarrolla el reloj y ajustar | 1 |

### 0.5 Simulador de reloj (17 h). Cambia en v7.
Programa que se hace pasar por uno o varios relojes y envía datos y alertas falsos, y que escala a los contactos como lo hará el reloj. Permite desarrollar y probar la plataforma sin el reloj real.

Depende de: 0.4. Listo cuando: con una orden se simulan relojes que sincronizan, envían alertas, escalan a los contactos, reintentan tras perder la señal y reciben avisos, y se pueden provocar silencios y fallas.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 0.5.1 | Decidir cómo se hará el simulador (script) y su estructura | 1 |
| [ ] | 0.5.2 | Generar sincronizaciones cada 2 h con lecturas acumuladas y su hora original | 3 |
| [ ] | 0.5.3 | Generar alertas (caída con inmovilidad, FC o SpO2 con 2 lecturas consecutivas, tendencia anormal, FC y SpO2 simultáneas) | 2 |
| [ ] | 0.5.4 | Simular la cancelación por el paciente y los reintentos de envío tras perder la señal | 2 |
| [ ] | 0.5.5 | Simular silencios, batería al 15 %, `event_id` repetido y hora desfasada | 2 |
| [ ] | 0.5.6 | Recibir los avisos por FCM (o simularlos) y registrar su llegada | 2 |
| [ ] | 0.5.7 | Simular el escalamiento del reloj: pedir el aviso a cada contacto según prioridad y horario, esperar, consultar el estado, pasar al siguiente y detenerse al recibir la confirmación | 3 |
| [ ] | 0.5.8 | Parámetros por línea de comandos y documentación de uso | 2 |

### 0.6 Entorno local con Docker (8 h)
Preparar el entorno local con Docker para levantar la base de datos y el backend con una sola orden.

Depende de: 0.3. Listo cuando: un integrante nuevo levanta base de datos y backend con una sola orden.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 0.6.1 | Instalar Docker en Windows (Docker Desktop o WSL2) y verificar que funciona | 2 |
| [x] | 0.6.2 | `docker-compose` con PostgreSQL (versión objetivo, volumen, credenciales por `.env`) | 2 |
| [ ] | 0.6.3 | Perfiles de Spring (local, prueba y piloto) y configuración por variables de entorno | 2 |
| [x] | 0.6.4 | `.env` de ejemplo sin secretos, `.gitignore` y README de arranque | 2 |

### 0.7 Trámites de WhatsApp Business Platform (8 h). Nueva.
Abrir la cuenta de Meta Business, registrar el número y conseguir la aprobación de las plantillas. Tiene días de espera ajenos al equipo: empezar en el sprint 1.

Depende de: —. Listo cuando: el número está registrado, las plantillas están aprobadas y hay un número de prueba para desarrollo.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 0.7.1 | Definir el titular de la cuenta Meta Business y el número dedicado | 2 |
| [ ] | 0.7.2 | Crear la cuenta, verificar el negocio y registrar el número | 2 |
| [ ] | 0.7.3 | Redactar y enviar a aprobación las plantillas: alerta leve, alerta grave (con instrucción de llamar al 131), simulacro "PRUEBA" | 3 |
| [ ] | 0.7.4 | Configurar el número de prueba de Meta para desarrollo | 1 |

### 0.8 Reunión con TI municipal (4 h). Nueva.
Acordar con TI cómo se alojará la plataforma en el servidor municipal.

Depende de: —. Listo cuando: hay un acta con los acuerdos sobre acceso, red, certificado y soporte.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 0.8.1 | Preparar las preguntas: sistema operativo, Docker, subdominio, puerto 443, certificado, salida a Meta, Firebase y SMTP, UPS, soporte fuera de horario, acceso del equipo, respaldos | 1 |
| [ ] | 0.8.2 | Reunión con TI y acta de acuerdos | 2 |
| [ ] | 0.8.3 | Ajustar el diseño de despliegue según las respuestas | 1 |

### 0.9 Simulador de contactos de WhatsApp (8 h). Nueva.
Programa que imita la API de Meta y a los contactos: acepta los envíos y manda al webhook respuestas y estados de entrega falsos. Permite probar el escalamiento sin enviar mensajes reales.

Depende de: 0.4. Listo cuando: el servidor envía "mensajes" al simulador y este responde al webhook confirmando, rechazando o sin responder, según el escenario.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 0.9.1 | Servidor falso que imita los endpoints de envío de la API de Meta | 3 |
| [ ] | 0.9.2 | Enviar al webhook respuestas de botón y estados de entrega (confirma, no puede, no responde, mensaje fallido) | 3 |
| [ ] | 0.9.3 | Firmar los mensajes simulados como lo hace Meta y documentar su uso | 2 |
 
---

## Fase 1: núcleo del backend (145 h)

### 1.1 Base de datos y migraciones (16 h). Cambia.
Crear todas las tablas mediante migraciones versionadas con Flyway, separando los datos de identidad de los de salud.

Depende de: 0.4, 0.6. Listo cuando: las migraciones crean toda la base desde cero y las pruebas con PostgreSQL real pasan.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 1.1.1 | Migración inicial: tablas de identidad (paciente, contactos con WhatsApp, correo, opt-in, prioridad y horario) | 3 |
| [x] | 1.1.2 | Tablas de dispositivos y vínculo reloj-paciente, con historial de vínculos | 3 |
| [x] | 1.1.3 | Tablas de telemetría (mediciones, alertas del reloj y sincronizaciones) con índices por paciente y tiempo | 3 |
| [x] | 1.1.4 | Tablas de perfil clínico (umbrales, reglas de tendencia en `JSONB`, GPS sí o no) y versión de configuración | 3 |
| [x] | 1.1.5 | Tablas de alertas (con su seguimiento), mensajes enviados, avisos FCM y auditoría | 2 |
| [ ] | 1.1.6 | Datos de ejemplo inventados y pruebas de migración con Testcontainers | 2 |

### 1.2 Pacientes y relojes (20 h)
Registrar pacientes y relojes, y vincular cada reloj a un paciente, con reemplazo y desvinculación sin perder el historial.

Depende de: 1.1. Listo cuando: se puede dar de alta un paciente, vincularle un reloj, reemplazarlo y desvincularlo conservando el historial.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 1.2.1 | Crear, editar y dar de baja pacientes (API y reglas) | 5 |
| [x] | 1.2.2 | Registrar relojes con ID único | 3 |
| [x] | 1.2.3 | Vincular reloj con paciente y generar el token del reloj | 4 |
| [x] | 1.2.4 | Reemplazar reloj conservando el historial, y desvincular revocando el token | 4 |
| [x] | 1.2.5 | Validaciones (un reloj, un paciente activo; evitar vínculos duplicados) | 2 |
| [x] | 1.2.6 | Pruebas unitarias y de integración | 2 |

### 1.3 Login del personal con Spring Security (24 h)
Inicio de sesión del personal del PRLAC: contraseña, segundo factor, roles y protección contra intentos repetidos.

Depende de: 0.3. Listo cuando: un usuario inicia sesión con contraseña y segundo factor, y cada endpoint respeta su rol.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 1.3.1 | Modelo de usuarios del sistema y roles (administrador, coordinador, personal) | 3 |
| [x] | 1.3.2 | Alta de usuarios, hash de contraseñas (BCrypt o Argon2) y política de contraseñas | 3 |
| [x] | 1.3.3 | Endpoint de login y token de acceso de vida corta | 4 |
| [ ] | 1.3.4 | Token de renovación guardado con hash, revocación y cierre de sesión | 4 |
| [ ] | 1.3.5 | Segundo factor TOTP (alta, verificación y códigos de respaldo) | 5 |
| [x] | 1.3.6 | Bloqueo por intentos fallidos y mensajes de error uniformes | 2 |
| [x] | 1.3.7 | Autorización por rol en los endpoints y pruebas de acceso | 3 |

### 1.4 API del reloj: sincronización y alertas (26 h). Cambia.
Endpoints que usa el reloj: sincronización cada 2 horas, alertas y cancelación por el paciente. Valida todo y evita duplicados aunque el reloj reintente tras perder la señal. Los endpoints del escalamiento están en 2.10.

Depende de: 1.1. Listo cuando: el simulador sincroniza y envía alertas, todo se guarda sin duplicados y los datos inválidos se rechazan.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 1.4.1 | Autenticación del reloj por token (rol `DEVICE`), separada del login del personal | 4 |
| [x] | 1.4.2 | Endpoint de sincronización (estado del reloj, tipo de conexión y lecturas acumuladas) | 4 |
| [x] | 1.4.3 | Endpoint de creación de alerta, con evidencia de lecturas | 3 |
| [x] | 1.4.4 | Endpoint de cancelación por el paciente (alerta en estado `CANCELADA`, para métricas) | 1 |
| [x] | 1.4.5 | Validación estricta (rangos, formato, marca de tiempo, hora desfasada) y rechazo con error claro | 4 |
| [x] | 1.4.6 | Idempotencia por `event_id` y tolerancia a reintentos y mensajes que llegan tarde tras perder la señal | 3 |
| [x] | 1.4.7 | Guardado de mediciones con hora de la medición y hora de recepción | 3 |
| [ ] | 1.4.8 | Pruebas con el simulador | 4 |

### 1.5 Perfil clínico y contactos por paciente (20 h). Cambia en v7. Hecha.
Configuración clínica de cada paciente: umbrales, reglas de tendencia, contactos con canal, prioridad, horario y opt-in, y uso de GPS. Cada paciente tiene su propia lista de contactos. Todo cambio crea una versión de configuración nueva que viaja al reloj.

Depende de: 1.1. Listo cuando: cada paciente tiene umbrales, contactos con WhatsApp o correo y su opt-in, prioridad y horario, y opciones de GPS guardados y validados.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 1.5.1 | Umbrales base del sistema (tablas del Anexo B) como configuración | 3 |
| [x] | 1.5.2 | Ajuste de umbrales y reglas de tendencia por paciente, registrando quién y cuándo | 4 |
| [x] | 1.5.3 | Contactos por paciente con WhatsApp y/o correo, opt-in, prioridad y horario (el SAMU no puede ser contacto automático) | 5 |
| [x] | 1.5.4 | Opciones por paciente: GPS sí o no (solo para ubicar; sin zona segura) | 2 |
| [x] | 1.5.5 | Validaciones (umbral mínimo menor que el máximo, prioridades sin repetir, a toda hora al menos un contacto disponible con opt-in vigente o correo, para las alertas medias y graves) | 3 |
| [x] | 1.5.6 | Pruebas | 3 |

Notas (25-10-2026): los umbrales base son provisionales hasta que los valide el equipo clínico. La tendencia se activa o desactiva por paciente; sus parámetros esperan el Anexo B. La cobertura a toda hora se muestra como aviso en la ficha, sin bloquear el guardado. Las pantallas de contactos y umbrales ya están en la ficha; el aviso al reloj (3.3.4 y 3.3.5) depende de la 1.7 y la 2.3.

### 1.6 Auditoría y cifrado de campos sensibles (16 h). Cambia.
Registrar quién accede o modifica datos y cifrar en la base los datos personales sensibles.

Depende de: 1.1, 1.3. Listo cuando: toda lectura o cambio de datos queda registrado y los datos personales están cifrados en la base.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 1.6.1 | Auditoría de accesos y cambios (quién, qué, cuándo), sin datos personales en los registros | 4 |
| [ ] | 1.6.2 | Auditoría de lecturas de datos de pacientes y de exportaciones | 3 |
| [ ] | 1.6.3 | Cifrado de campos sensibles (nombre, RUT, dirección, WhatsApp y correo de los contactos, contenido de las versiones de configuración) en la base | 5 |
| [ ] | 1.6.4 | Gestión de la llave de cifrado en local y en el servidor municipal (acceso restringido, acordado con TI) | 2 |
| [ ] | 1.6.5 | Pruebas | 2 |

### 1.7 API de configuración del reloj (13 h). Cambia en v7.
Entregar al reloj su configuración solo cuando cambia y controlar qué versión aplica cada reloj. Vuelve a incluir los contactos y los plazos, porque el reloj escala. Los números y correos no viajan: el reloj pide los avisos por identificador de contacto.

Depende de: 1.5, 1.9. Listo cuando: el reloj descarga su configuración solo si cambió, con sus contactos y el calendario hábil, y la plataforma sabe qué versión aplica cada reloj.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 1.7.1 | Endpoint que entrega umbrales, reglas de tendencia, GPS sí o no, contactos (sin números ni correos), plazo de espera, horario hábil, feriados y versión, sin reenviar si no cambió | 4 |
| [ ] | 1.7.2 | Incluir módulos activos y modo conservador | 1 |
| [ ] | 1.7.3 | Versionado de la configuración y registro de cuándo la aplicó cada reloj | 3 |
| [ ] | 1.7.4 | Registro del token de Firebase de cada reloj (se renueva cuando el reloj lo informa) | 2 |
| [ ] | 1.7.5 | Valores base cuando el paciente no tiene ajustes propios | 1 |
| [ ] | 1.7.6 | Pruebas | 2 |

### 1.8 Módulo de presión arterial. Eliminada.
El Samsung Health Sensor SDK no entrega presión arterial, por lo que el módulo se elimina (−16 h).

### 1.9 Enrutamiento por gravedad y calendario hábil (10 h). Cambia en v7.
Definir a quién se avisa según la gravedad y el horario hábil con feriados. Viaja al reloj en la configuración, porque el reloj escala; el servidor lo usa para validar las alertas recibidas y para las alertas técnicas.

Depende de: 1.5. Listo cuando: la regla de enrutamiento está configurada, cada paciente tiene contactos con prioridad y horario, y el reloj y el servidor conocen el horario hábil y los feriados.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 1.9.1 | Regla de enrutamiento base (Anexo B): una alerta leve fuera del horario hábil queda pendiente; una media o grave avisa siempre a los contactos disponibles a esa hora, por prioridad | 4 |
| [ ] | 1.9.2 | Calendario hábil (horario y feriados del año) administrable, en zona `America/Santiago` | 3 |
| [ ] | 1.9.3 | Pruebas, incluidos los días de cambio de hora | 3 |
 
---

## Fase 2: alertas, escalamiento y avisos (164 h)

### 2.1 Recepción y validación de las alertas del reloj (16 h). Cambia.
Recibir y registrar cada alerta del reloj. El servidor nunca descarta una alerta: si algo no calza, la acepta y marca la discrepancia.

Depende de: 1.4, 1.5. Listo cuando: cada alerta queda registrada y visible, y ninguna se descarta.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.1.1 | Tipos de alerta (caída, FC, SpO2, tendencia, FC y SpO2 simultáneas; sin SOS) y niveles de gravedad | 4 |
| [ ] | 2.1.2 | Comparar la evidencia con la configuración vigente: si no calza, aceptar igual y marcar la discrepancia | 3 |
| [ ] | 2.1.3 | Alertas en dos etapas (caída que sube a grave por inmovilidad) y actualización de una alerta existente | 3 |
| [ ] | 2.1.4 | Deduplicación y límite de frecuencia por paciente (reloj defectuoso que inunda de alertas) | 3 |
| [ ] | 2.1.5 | Pruebas con casos límite | 3 |

### 2.2 Reglas de ausencia en el servidor (24 h)
Tareas programadas que revisan si algún reloj dejó de sincronizar (silencio de datos), si hay inactividad prolongada o problemas de batería o de uso. Con LTE, el silencio delata falta de señal, un reloj apagado o un plan de datos vencido.

Depende de: 1.4. Listo cuando: el sistema detecta por sí solo que un reloj dejó de sincronizar, sin alertas repetidas.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.2.1 | Tarea programada (vigilante) que revisa la última sincronización de cada reloj | 4 |
| [ ] | 2.2.2 | Regla de silencio de datos (4 horas sin sincronizar) | 4 |
| [ ] | 2.2.3 | Regla de inactividad prolongada como respaldo del servidor | 4 |
| [ ] | 2.2.4 | Reloj no puesto y batería baja informados en la sincronización: alertar si el estado persiste | 6 |
| [ ] | 2.2.5 | Evitar alertas repetidas mientras una siga abierta | 3 |
| [ ] | 2.2.6 | Pruebas | 3 |

### 2.3 Avisos al reloj por FCM (12 h). Cambia en v7.
Avisar al reloj que un contacto confirmó su alerta o que un profesional la resolvió (en ambos casos el reloj detiene su escalamiento), o que hay configuración nueva (por ejemplo, un contacto cambió).

Depende de: 2.1, 1.7. Listo cuando: el reloj (o el simulador) recibe los avisos y la plataforma registra si llegaron.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.3.1 | Crear el proyecto de Firebase (gratuito) y configurar el SDK de administración en el backend, con la llave como secreto | 3 |
| [ ] | 2.3.2 | Enviar avisos de alta prioridad (alerta confirmada, alerta resuelta, configuración nueva), solo con identificadores | 3 |
| [ ] | 2.3.3 | Registrar la recepción informada por el reloj y reintentar si no llega en un plazo | 3 |
| [ ] | 2.3.4 | Pruebas con el simulador | 3 |

### 2.4 Correo institucional (12 h). Cambia.
Avisos por correo desde la cuenta institucional: silencio de datos, alertas graves y respaldo cuando WhatsApp falla, sin datos clínicos en el mensaje.

Depende de: 2.2. Listo cuando: un silencio de datos y una alerta grave generan correos desde la cuenta institucional, y en local se prueba sin enviar correos reales.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.4.1 | Interfaz `NotificationChannel` y canal simulado que solo registra el mensaje (local) | 2 |
| [ ] | 2.4.2 | Canal de correo con Spring Mail y la cuenta SMTP institucional (credenciales como secreto) | 3 |
| [ ] | 2.4.3 | Plantillas mínimas: silencio de datos, alerta grave y respaldo de WhatsApp (alias del paciente, sin datos clínicos) | 3 |
| [ ] | 2.4.4 | Manejo de errores, reintentos y pruebas con un servidor de correo de prueba | 4 |

### 2.5 Registro y estado de cada alerta (12 h). Cambia.
Línea de tiempo completa de cada alerta (mensajes, entregas, respuestas, saltos de contacto) y su resolución por un profesional. Es la trazabilidad oficial del sistema.

Depende de: 2.1. Listo cuando: cada alerta muestra la línea de tiempo completa y quién la resolvió.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.5.1 | Guardar cada paso: recepción, cada solicitud de aviso del reloj, cada WhatsApp o correo con sus estados de entrega, respuestas, salto de contacto, sin respuesta | 3 |
| [ ] | 2.5.2 | Resolución de la alerta por un profesional (observaciones, usuario y clasificación; una positiva abre su seguimiento), que envía el aviso FCM para que el reloj detenga el escalamiento | 3 |
| [ ] | 2.5.3 | Marcar como prioridad máxima las alertas `SIN_RESPUESTA` de toda la lista de contactos | 3 |
| [ ] | 2.5.4 | Pruebas | 3 |

### 2.6 Pruebas de escenarios con los simuladores (22 h). Cambia.
Probar automáticamente cada evento del Anexo B y cada camino del escalamiento, con el simulador de reloj y el de contactos, incluidas las fallas.

Depende de: 0.5, 0.9, 2.10. Listo cuando: cada evento y cada camino del escalamiento se prueban automáticamente y los resultados están documentados.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.6.1 | Lista de escenarios del Anexo B y del escalamiento con su resultado esperado | 4 |
| [ ] | 2.6.2 | Automatizar los escenarios con el simulador de reloj y el de contactos | 8 |
| [ ] | 2.6.3 | Casos de falla: WhatsApp caído, webhook que no llega, correo caído, reloj sin señal o reinicio del servidor durante un escalamiento, aviso FCM atrasado o no entregado, silencio de datos | 6 |
| [ ] | 2.6.4 | Informe de resultados y correcciones | 4 |

### 2.7 Red de seguridad del servidor (8 h). Cambia.
Revisar las lecturas recibidas para detectar posibles eventos que el reloj no alertó y mostrarlos en un informe. No avisa a contactos: sirve para auditar y calibrar las reglas del reloj.

Depende de: 1.4, 1.5. Listo cuando: tras cada sincronización, el servidor lista los posibles eventos no alertados, sin avisar a nadie.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.7.1 | Revisión retrospectiva de las lecturas recibidas contra los umbrales vigentes (FC y SpO2) | 3 |
| [ ] | 2.7.2 | Cruzar con las alertas del reloj en la misma ventana de tiempo y registrar los hallazgos | 2 |
| [ ] | 2.7.3 | Informe de hallazgos para el coordinador | 1 |
| [ ] | 2.7.4 | Pruebas | 2 |

### 2.8 Simulacro de alerta (6 h). Cambia.
Lanzar desde el dashboard una alerta de prueba marcada "PRUEBA" para verificar que toda la cadena de aviso funciona.

Depende de: 2.3, 2.9. Listo cuando: desde el dashboard se dispara un simulacro y queda registrado si cada paso funcionó.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.8.1 | Iniciar el simulacro: aviso FCM al reloj y plantilla "PRUEBA" de WhatsApp | 2 |
| [ ] | 2.8.2 | Registrar cada paso (alerta recibida, WhatsApp entregado, confirmación, aviso al reloj) | 2 |
| [ ] | 2.8.3 | Excluir los simulacros de reportes y métricas clínicas, y pruebas | 2 |

### 2.9 Canal de WhatsApp (30 h). Nueva.
Enviar las plantillas de alerta por la Cloud API de Meta y recibir las respuestas y estados de entrega por webhook. Implementa la interfaz `NotificationChannel`. Lo usa el servidor cuando el reloj pide avisar a un contacto (2.10); la clave de Meta queda solo en el servidor.

Depende de: 0.7, 0.9, 2.4.1. Listo cuando: el servidor envía una plantilla con botones, recibe la respuesta del contacto por webhook validando la firma, y registra los estados de entrega.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.9.1 | Cliente de la Cloud API de Meta, con el token como secreto | 4 |
| [ ] | 2.9.2 | Envío de plantillas con botones de respuesta rápida ("Voy en camino", "No puedo") | 4 |
| [ ] | 2.9.3 | Webhook: verificación inicial de Meta y validación de la firma de cada mensaje | 4 |
| [ ] | 2.9.4 | Recepción de los botones y vínculo con la alerta y el contacto | 4 |
| [ ] | 2.9.5 | Estados de entrega de Meta (enviado, entregado, leído, fallido) en la línea de tiempo | 3 |
| [ ] | 2.9.6 | Respetar el opt-in: sin opt-in vigente no se envía, se responde al reloj para que pase al siguiente contacto y se marca en el dashboard | 3 |
| [ ] | 2.9.7 | Manejo de errores, reintentos y límites de Meta; si WhatsApp falla, respaldo por correo | 4 |
| [ ] | 2.9.8 | Usar el simulador de contactos (0.9) en local y probar con el número de prueba de Meta | 4 |

### 2.10 Escalamiento dirigido por el reloj: lado del servidor (22 h). Cambia en v7.
El reloj decide a quién avisar, espera y pasa al siguiente contacto (ver la app del reloj). El servidor envía cada aviso que el reloj pide, le informa cuando un contacto confirma y registra el recorrido completo. Reemplaza al motor de escalamiento del servidor de la v6.

Depende de: 1.9, 2.1, 2.3, 2.9. Listo cuando: con el simulador de reloj, una alerta recorre la lista de contactos, cada aviso pedido sale por WhatsApp o correo, el reloj se detiene al confirmarse o resolverse, y una alerta sin noticias del reloj avisa a los profesionales.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 2.10.1 | Máquina de estados de la alerta en el servidor según lo que informa el reloj (recibida, avisando al contacto n, confirmada, sin respuesta, resuelta, pendiente de día hábil, cancelada) | 4 |
| [ ] | 2.10.2 | Endpoint de solicitud de aviso: el reloj pide avisar a un contacto; el servidor verifica que sea del paciente y tenga opt-in, y envía por WhatsApp o correo. Una solicitud repetida no envía dos veces | 5 |
| [ ] | 2.10.3 | Endpoint de estado de la alerta, que el reloj consulta antes de pasar al siguiente contacto (respaldo si FCM se atrasa) | 2 |
| [ ] | 2.10.4 | Al recibir la confirmación por webhook: marcar la alerta como confirmada y avisar al reloj por FCM | 2 |
| [ ] | 2.10.5 | Alertas medias y graves: correo a los profesionales y destacado en el dashboard, en paralelo al escalamiento del reloj | 2 |
| [ ] | 2.10.6 | Registrar el fin de la lista informado por el reloj (`SIN_RESPUESTA`), y vigilante: alerta abierta sin noticias del reloj en un plazo → correo a los profesionales | 3 |
| [ ] | 2.10.7 | Pruebas, incluidos un reloj sin señal y un reinicio del servidor a mitad de un escalamiento | 4 |
 
---

## Fase 3: dashboard (158 h)

### 3.1 Bocetos de pantallas (16 h)
Diseñar bocetos de las pantallas junto al personal del PRLAC antes de programar.

Depende de: —. Listo cuando: el personal del PRLAC aprobó los bocetos.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 3.1.1 | Reunirse con el personal del PRLAC para entender qué necesitan ver y hacer | 4 |
| [ ] | 3.1.2 | Mapa de pantallas y flujos | 3 |
| [ ] | 3.1.3 | Bocetos de baja fidelidad (login, lista de pacientes, ficha, alertas, tendencias, reporte) | 6 |
| [ ] | 3.1.4 | Revisión con el PRLAC y ajustes | 3 |

### 3.2 Estructura base del dashboard (16 h)
Proyecto con React y Tailwind, inicio de sesión y navegación según el rol.

Depende de: 0.1, 1.3. Listo cuando: se puede iniciar sesión y navegar según el rol.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 3.2.1 | Crear el proyecto con React, TypeScript, Vite y Tailwind | 3 |
| [x] | 3.2.2 | Estructura por funcionalidades y componentes base | 3 |
| [ ] | 3.2.3 | Generar los tipos desde OpenAPI y el cliente de la API | 3 |
| [ ] | 3.2.4 | Pantalla de login y manejo de sesión (renovación de token con cookie) | 4 |
| [x] | 3.2.5 | Navegación y rutas protegidas por rol | 3 |

### 3.3 Gestión de pacientes, relojes y contactos (32 h). Cambia.
Pantallas para administrar pacientes, relojes, contactos y umbrales sin tocar la base de datos.

Depende de: 1.2, 1.5. Listo cuando: el personal configura un paciente completo desde el dashboard sin tocar la base de datos.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [x] | 3.3.1 | Lista de pacientes con búsqueda y filtros | 5 |
| [x] | 3.3.2 | Formulario de alta y edición de paciente | 5 |
| [x] | 3.3.3 | Vinculación y reemplazo de reloj | 5 |
| [ ] | 3.3.4 | Edición de contactos: WhatsApp, correo, opt-in, prioridad y horario (crea una configuración nueva que se avisa al reloj por FCM) | 6 |
| [ ] | 3.3.5 | Edición de umbrales y reglas de tendencia (llegan al reloj en su siguiente sincronización) | 5 |
| [ ] | 3.3.6 | Validaciones de formularios y mensajes de error | 3 |
| [ ] | 3.3.7 | Pruebas | 3 |

### 3.4 Estado actual y alertas activas (26 h). Cambia.
Panel principal con el estado de cada paciente y las alertas activas, su línea de tiempo, su evidencia y el botón "Resolver alerta".

Depende de: 2.5, 2.10. Listo cuando: el personal ve de un vistazo qué pacientes tienen alertas, a quién se está avisando, y puede resolverlas.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 3.4.1 | Panel de estado general (pacientes, última sincronización, configuración pendiente, estado del reloj, tipo de conexión) | 6 |
| [ ] | 3.4.2 | Lista de alertas activas con gravedad, antigüedad y contacto en curso (el último que pidió el reloj) | 6 |
| [ ] | 3.4.3 | Detalle de la alerta: línea de tiempo (solicitudes del reloj, WhatsApp, correos, avisos FCM) y evidencia de lecturas | 6 |
| [ ] | 3.4.4 | Botón "Resolver alerta" con observaciones y clasificación: avisa al reloj para que detenga el escalamiento | 3 |
| [ ] | 3.4.5 | Actualización automática por consulta periódica | 2 |
| [ ] | 3.4.6 | Pruebas | 3 |

### 3.5 Historial y gráficos de tendencias (28 h). Cambia.
Gráficos de la evolución de FC y SpO2 de cada paciente, con la hora real de cada medición y los eventos marcados.

Depende de: 1.4. Listo cuando: se pueden ver las tendencias de FC y SpO2 con sus eventos, usando la hora original de cada medición.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 3.5.1 | Consulta de tendencias en el backend, agrupadas por tiempo | 5 |
| [ ] | 3.5.2 | Gráfico de frecuencia cardíaca | 5 |
| [ ] | 3.5.3 | Gráfico de SpO2 | 5 |
| [ ] | 3.5.4 | Filtros por rango de fechas | 3 |
| [ ] | 3.5.5 | Marcas de eventos y alertas sobre los gráficos | 4 |
| [ ] | 3.5.6 | Ubicaciones si el paciente tiene GPS activo | 3 |
| [ ] | 3.5.7 | Pruebas | 3 |

### 3.6 Reportes diarios y semanales (28 h)
Reportes automáticos por paciente (promedios, máximos y mínimos) para el seguimiento preventivo. Son apoyo para el profesional, no diagnóstico.

Depende de: 3.5. Listo cuando: cada día y cada semana se genera solo un reporte por paciente, visible en el dashboard.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 3.6.1 | Definir con el PRLAC qué indicadores incluye el reporte | 3 |
| [ ] | 3.6.2 | Tarea programada que compila los datos diarios y semanales por paciente con consultas SQL | 6 |
| [ ] | 3.6.3 | Guardar los reportes y consultarlos por día y por semana | 4 |
| [ ] | 3.6.4 | Vista del reporte en el dashboard | 6 |
| [ ] | 3.6.5 | Comparación con semanas anteriores | 4 |
| [ ] | 3.6.6 | Pruebas y revisión con el PRLAC | 5 |

### 3.7 Bandeja de alertas pendientes de día hábil (4 h)
Reúne las alertas leves llegadas fuera de horario para revisarlas a primera hora del siguiente día hábil.

Depende de: 1.9, 3.4. Listo cuando: las alertas leves fuera de horario aparecen juntas, ordenadas por antigüedad.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 3.7.1 | Vista de la bandeja con filtros por paciente y tipo | 2 |
| [ ] | 3.7.2 | Marcar como revisada con observaciones, y pruebas | 2 |

### 3.8 Inventario de equipos (8 h). Cambia en v7.
Estado de todos los relojes y de su SIM LTE con el vencimiento del plan.

Depende de: 1.2, 1.7. Listo cuando: el equipo ve en una sola pantalla el estado de los relojes y de sus SIM.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 3.8.1 | Consulta en el backend: versión de la app, batería, última sincronización, configuración pendiente, tipo de conexión (LTE o Wi-Fi) | 2 |
| [ ] | 3.8.2 | Registro de la SIM de cada reloj, con su número y la fecha de vencimiento del plan de datos | 2 |
| [ ] | 3.8.3 | Vista con alertas visuales (sin sincronizar, versión antigua, batería baja, plan por vencer) | 3 |
| [ ] | 3.8.4 | Pruebas | 1 |
 
---

## Fase 4: seguridad, servidor municipal y calidad (124 h)

Se hace cuando el sistema ya funciona en local, antes del piloto.

### 4.0 Preparar el servidor municipal con TI (8 h). Cambia (reemplaza la cuenta de Google Cloud).
Dejar el servidor listo para recibir la plataforma, según lo acordado en 0.8.

Depende de: 0.8. Listo cuando: el equipo accede al servidor, Docker funciona, el subdominio apunta al servidor y la salida a Meta, Firebase y SMTP está abierta.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 4.0.1 | Accesos del equipo (VPN o SSH), con segundo factor si TI lo permite | 2 |
| [ ] | 4.0.2 | Instalar o verificar Docker en el servidor | 2 |
| [ ] | 4.0.3 | Subdominio público y puerto 443 hacia el servidor | 1 |
| [ ] | 4.0.4 | Firewall: salida hacia Meta, Firebase y el SMTP institucional | 2 |
| [ ] | 4.0.5 | Verificar UPS y protocolo de soporte fuera de horario | 1 |

### 4.1 Infraestructura en el servidor municipal (24 h). Cambia.
Levantar la plataforma completa en el servidor municipal con exposición mínima a internet, certificado, secretos y respaldos.

Depende de: 4.0. Listo cuando: la plataforma corre en el servidor, solo la API del reloj y el webhook son públicos, y hay respaldos automáticos probados.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 4.1.1 | Docker Compose de producción (backend, PostgreSQL, Nginx, dashboard compilado) con reinicio automático | 6 |
| [ ] | 4.1.2 | Nginx con HTTPS: solo la API del reloj y el webhook públicos, dashboard solo en red interna o VPN, límite de peticiones | 5 |
| [ ] | 4.1.3 | Certificado TLS (Let's Encrypt o institucional) con renovación automática | 3 |
| [ ] | 4.1.4 | Secretos y llave de cifrado en archivos protegidos fuera del repositorio | 3 |
| [ ] | 4.1.5 | Respaldos automáticos de PostgreSQL con copia fuera del servidor | 4 |
| [ ] | 4.1.6 | Documentar la infraestructura | 3 |

### 4.2 Despliegue y entornos (14 h). Cambia.
Construir las imágenes en GitHub Actions y desplegarlas en el servidor municipal, con entornos separados y posibilidad de volver atrás.

Depende de: 4.1. Listo cuando: un cambio aprobado se despliega al entorno de pruebas con un script y se puede volver a la versión anterior.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 4.2.1 | Construir la imagen del backend (Jib o Dockerfile) | 3 |
| [ ] | 4.2.2 | Pipeline en GitHub Actions: compilar, probar y publicar las imágenes | 3 |
| [ ] | 4.2.3 | Script de despliegue al servidor municipal, según el acceso que dé TI | 3 |
| [ ] | 4.2.4 | Entornos de pruebas y de piloto separados en el servidor | 2 |
| [ ] | 4.2.5 | Migraciones automáticas con Flyway al desplegar | 2 |
| [ ] | 4.2.6 | Plan de reversión | 1 |

### 4.3 Monitoreo, registros y alertas operativas (14 h). Cambia.
Saber si el servicio se cae o falla algún canal, incluso si la municipalidad queda sin internet.

Depende de: 4.2. Listo cuando: el equipo y TI reciben aviso si el servicio cae o fallan los envíos.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 4.3.1 | Registros estructurados sin datos personales | 3 |
| [ ] | 4.3.2 | Métricas básicas (peticiones, errores, relojes sin sincronizar, WhatsApp fallidos, avisos FCM no entregados) | 3 |
| [ ] | 4.3.3 | Monitoreo externo de disponibilidad, desde fuera de la municipalidad | 2 |
| [ ] | 4.3.4 | Alertas operativas al equipo y a TI (servicio caído, webhook sin mensajes, errores de Meta, correo o FCM) | 4 |
| [ ] | 4.3.5 | Panel de monitoreo simple | 2 |

### 4.4 Revisión de seguridad y protección de datos (22 h). Cambia.
Revisar la seguridad completa, probar respaldos y programar el borrado de datos según el consentimiento.

Depende de: 1.6, 4.2. Listo cuando: la lista de verificación está completa y los riesgos pendientes están revisados con los tutores.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 4.4.1 | Lista de verificación (accesos, roles, cabeceras, CORS, secretos) | 4 |
| [ ] | 4.4.2 | Pruebas de acceso entre roles y entre pacientes | 4 |
| [ ] | 4.4.3 | Revisar dependencias con vulnerabilidades conocidas | 2 |
| [ ] | 4.4.4 | Revisar la exposición pública mínima y la validación de firma del webhook | 2 |
| [ ] | 4.4.5 | Retención y borrado: eliminar los datos 6 meses después del fin del piloto, según el consentimiento | 4 |
| [ ] | 4.4.6 | Respaldos: probar una restauración | 3 |
| [ ] | 4.4.7 | Revisión con los tutores y registro de riesgos | 3 |

### 4.5 Pruebas de punta a punta y de carga ligera (24 h). Cambia.
Probar el flujo completo de forma automática, también con 30 relojes simulados y ante fallas.

Depende de: 2.6, 3.4. Listo cuando: el flujo completo funciona de forma automática, también ante fallas.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 4.5.1 | Automatizar el flujo: simulador, API del reloj, alerta, escalamiento del reloj con WhatsApp simulado, confirmación, aviso FCM y resolución | 8 |
| [ ] | 4.5.2 | Pruebas de carga ligera (30 relojes simulados) | 5 |
| [ ] | 4.5.3 | Pruebas de falla: reinicio del servidor, base caída, WhatsApp caído, correo caído, servidor sin internet, reloj sin señal | 5 |
| [ ] | 4.5.4 | Pruebas del dashboard de punta a punta | 4 |
| [ ] | 4.5.5 | Corrección de los errores encontrados | 2 |

### 4.6 Medir el tiempo entre evento y aviso (8 h). Cambia.
Medir el tiempo entre la detección del evento y la confirmación del contacto, y compararlo con la meta del charter de 5 a 15 minutos.

Depende de: 4.5. Listo cuando: hay un informe que compara los tiempos medidos con la meta del charter.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 4.6.1 | Registrar marcas de tiempo (evento en el reloj, fin de la ventana de 30 s, recepción en el servidor, solicitud de aviso del reloj, WhatsApp enviado, entregado, confirmación, aviso FCM recibido) | 2 |
| [ ] | 4.6.2 | Medir con unos 50 eventos simulados | 3 |
| [ ] | 4.6.3 | Informe contra la meta y ajustes | 3 |

### 4.7 Métricas de validación (10 h)
Calcular sensibilidad, precisión y tasa de falsos negativos con escenarios de resultado conocido, como pide el objetivo 4 del charter.

Depende de: 4.5. Listo cuando: hay un informe repetible en el piloto (5.4).

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 4.7.1 | Registrar en cada escenario de prueba el resultado esperado y el obtenido | 3 |
| [ ] | 4.7.2 | Calcular sensibilidad, precisión y falsos negativos por tipo de evento | 3 |
| [ ] | 4.7.3 | Informe automático reutilizable en el piloto | 2 |
| [ ] | 4.7.4 | Revisión de resultados con el equipo | 2 |
 
---

## Fase 5: integración y preparación del piloto (96 h)

### 5.1 Integración con el reloj real (26 h). Cambia en v7.
Conectar el reloj real con la plataforma y probar sincronización, alertas, escalamiento, avisos y la conexión LTE.

Depende de: 4.5, 1.7, 2.3 y reloj disponible. Listo cuando: un reloj real sincroniza por LTE, envía alertas, escala a los contactos por WhatsApp a través del servidor, y recibe la configuración y los avisos.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 5.1.1 | Entorno de pruebas en el servidor municipal accesible desde internet | 3 |
| [ ] | 5.1.2 | Registrar el reloj real, generar su token y registrar su token de Firebase | 2 |
| [ ] | 5.1.3 | Probar sincronización y descarga de configuración, y compararlas con el contrato | 5 |
| [ ] | 5.1.4 | Probar caída y lecturas anormales reales o provocadas, con el escalamiento del reloj por WhatsApp a contactos de prueba, confirmación con botón y salto de contacto | 6 |
| [ ] | 5.1.5 | Probar los avisos de confirmación y resolución en el reloj (detienen su escalamiento) | 2 |
| [ ] | 5.1.6 | Probar la conexión LTE (y Wi-Fi si se usa), zonas con poca señal, la pérdida de señal y la reconexión | 4 |
| [ ] | 5.1.7 | Ajustar el contrato y las validaciones según los hallazgos | 4 |

### 5.2 Manuales (18 h). Cambia.
Manuales de configuración, operación diaria, instalación en terreno y fallas.

Depende de: 3.3. Listo cuando: el personal del PRLAC puede configurar y operar el sistema leyendo los manuales.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 5.2.1 | Manual de configuración por paciente, incluido el registro del opt-in de los contactos | 6 |
| [ ] | 5.2.2 | Manual de operación diaria del dashboard y de las alertas | 5 |
| [ ] | 5.2.3 | Manual de entrega en terreno (reloj, SIM y plan de datos, verificación de señal) | 2 |
| [ ] | 5.2.4 | Guía de qué hacer ante fallas (no llegan datos, no llegan WhatsApp, reloj sin señal, apagado o con el plan vencido) | 3 |
| [ ] | 5.2.5 | Revisión con el PRLAC | 2 |

### 5.3 Capacitación (8 h)
Capacitar al personal del PRLAC y del CESFAM con casos de práctica.

Depende de: 5.2. Listo cuando: el personal practicó los casos principales y sus observaciones están incorporadas.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 5.3.1 | Preparar material y casos de práctica | 3 |
| [ ] | 5.3.2 | Sesión con el personal del PRLAC y del CESFAM | 3 |
| [ ] | 5.3.3 | Recoger observaciones y ajustar | 2 |

### 5.4 Pruebas controladas con escenarios de riesgo (24 h)
Probar escenarios de riesgo con el reloj real y contactos de prueba antes del piloto.

Depende de: 5.1. Listo cuando: todos los tipos de alerta se probaron con el reloj real y los errores están corregidos.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 5.4.1 | Plan de pruebas con escenarios de riesgo | 4 |
| [ ] | 5.4.2 | Ejecutar los escenarios con el reloj real y contactos de prueba | 8 |
| [ ] | 5.4.3 | Verificar cada tipo de alerta y su escalamiento por WhatsApp y correo | 6 |
| [ ] | 5.4.4 | Registrar los resultados | 2 |
| [ ] | 5.4.5 | Corregir y repetir | 4 |

### 5.5 Alta de usuarios reales del piloto (12 h). Cambia.
Registrar a los pacientes del piloto con su consentimiento, contactos con opt-in, umbrales y reloj.

Depende de: 5.3. Listo cuando: cada paciente del piloto está registrado, con su reloj vinculado, sus contactos con opt-in y sus umbrales.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 5.5.1 | Confirmar los consentimientos informados firmados | 2 |
| [ ] | 5.5.2 | Registrar a los pacientes del piloto | 3 |
| [ ] | 5.5.3 | Cargar contactos, horarios y opt-in de WhatsApp por paciente | 3 |
| [ ] | 5.5.4 | Configurar los umbrales con el equipo clínico | 2 |
| [ ] | 5.5.5 | Vincular cada reloj y verificar un simulacro con WhatsApp | 2 |

### 5.6 Instalación en terreno (8 h, más unas 2 h por paciente). Nueva.
Preparar y probar el procedimiento de instalación en las casas. La instalación de cada paciente (unas 2 h, incluido el traslado y un simulacro) se estima aparte, según el número de pacientes del piloto.

Depende de: 5.1, 5.2. Listo cuando: el procedimiento está probado en una casa y existe el acta de instalación.

| Estado | # | Subtarea | Horas |
|---|---|---|---|
| [ ] | 5.6.1 | Procedimiento y lista de verificación: señal LTE en la casa y sus alrededores, SIM y plan activos, apagado automático desactivado, enrolamiento del reloj | 3 |
| [ ] | 5.6.2 | Probar el procedimiento completo en una casa de prueba | 3 |
| [ ] | 5.6.3 | Acta de instalación y registro en el inventario | 2 |
 
---

## Totales

| Concepto | Horas-persona |
|---|---|
| Fase 0 | 87 |
| Fase 1 | 145 |
| Fase 2 | 164 |
| Fase 3 | 158 |
| Fase 4 | 124 |
| Fase 5 | 96 |
| Total | 774 |
| Total con 30 % de margen | aprox. 1.006 |
| No incluido | Instalación por paciente (unas 2 h cada uno) |

Capacidad del equipo: 3 personas x 20 a 30 h = 60 a 90 h por semana.

## Propuesta de sprints

Sprints de 2 semanas (como define el charter), respetando las dependencias. Cada sprint suma entre 70 y 133 horas, por debajo de la capacidad del equipo (120 a 180 h por sprint) para dejar espacio al margen de error, reuniones y Discovery. Ya están hechas 0.1, 0.2 y 0.3.

| Sprint | Objetivo | Tareas | Horas |
|---|---|---|---|
| 1 | Base del proyecto, modelo de datos y trámites externos | 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 1.1, 1.2, 3.1 | 109 |
| 2 | Núcleo del backend y configuración del reloj | 0.9, 1.3, 1.4, 1.5, 1.6, 1.9, 1.7, 3.2 | 133 |
| 3 | Alertas, canales de aviso y gestión de pacientes | 2.1, 2.2, 2.3, 2.4, 2.9, 3.3 | 126 |
| 4 | Escalamiento y panel de alertas | 2.5, 2.7, 2.10, 3.4, 3.5, 3.8, 4.0 | 112 |
| 5 | Flujo completo probado y desplegado en el servidor municipal | 2.6, 2.8, 3.6, 3.7, 4.1, 4.2, 4.3 | 112 |
| 6 | Seguridad, métricas y manuales | 4.4, 4.5, 4.6, 4.7, 5.2, 5.3 | 90 |
| 7 | Reloj real e instalación del piloto | 5.1, 5.4, 5.5, 5.6 | 70 |
| | | Total (más 22 h ya hechas) | 752 |

Dentro de un sprint, las tareas con dependencia entre sí se hacen en orden (por ejemplo, 1.9 antes que 1.7, 2.5 antes que 3.4, y 4.2 antes que 4.3). La tarea 0.7 debe empezar el primer día del sprint 1, porque la aprobación de Meta puede tardar. El sprint 7 depende de que el reloj real esté disponible y haya pasado su prueba de factibilidad. Si el charter exige 6 sprints, los sprints 6 y 7 pueden unirse (160 h) solo si el equipo trabaja cerca de 30 h semanales cada uno.

## Hitos

| Hito | Al final de | Qué se puede demostrar |
|---|---|---|
| H1 | Sprint 2 | El simulador sincroniza con el backend en local y recibe su configuración; el dashboard permite iniciar sesión. Trámites de Meta y reunión con TI iniciados |
| H2 | Sprint 4 | Flujo completo en local: alerta del simulador, escalamiento dirigido por el reloj simulado con WhatsApp simulado, confirmación, aviso por FCM y resolución en el dashboard |
| H3 | Sprint 5 | Plataforma desplegada en el servidor municipal, escenarios automatizados y reportes funcionando |
| H4 | Sprint 6 | Revisión de seguridad hecha, tiempos medidos, informe de sensibilidad y manuales listos |
| H5 | Sprint 7 | Reloj real integrado, pruebas controladas aprobadas y pacientes del piloto instalados |

## Camino que no se puede acortar

0.4, 1.1, 1.4, 2.1, 2.9, 2.10, 2.6, 4.5, 5.1, 5.4

Dependencias externas que pueden atrasarlo: aprobación de Meta (0.7), acuerdos y accesos con TI (0.8 y 4.0), y llegada del reloj con la aprobación de Samsung.

## Fuera de este cronograma: la app del reloj

El reloj detecta las emergencias, envía las alertas y **escala a los contactos**: elige a quién avisar y cuándo, y le pide cada aviso al servidor. Su trabajo no está estimado aquí; conviene un cronograma aparte. La primera tarea debe ser una prueba de factibilidad con el reloj real.

Prioridad 1, prueba de factibilidad:

- Leer FC, SpO2 y acelerómetro con el Samsung Health Sensor SDK, con el modo desarrollador del Health Platform activado (el SDK no funciona en emulador).
- Postular al Samsung Partner Program para poder distribuir la app (tiene días de espera).
- Enviar por LTE pidiendo la red celular con `requestNetwork`, y por Wi-Fi si está disponible.
- Recibir avisos de FCM por LTE, y medir cuánto tardan.
- Medir el consumo de batería con LTE durante varios días.
- Comprobar el comportamiento del reloj al desvincularlo del teléfono usado en la configuración inicial.

Resto del trabajo del reloj:

- Motor de reglas local en Kotlin: caída con espera de inmovilidad, FC y SpO2 con 2 lecturas consecutivas, reglas de tendencia sobre el lote de 14 días, batería, reloj no puesto. Descarte de lecturas de SpO2 fallidas.
- Ventana de cancelación de 30 segundos con vibración.
- **Escalamiento local:**
  - elegir el contacto por prioridad y horario, según la gravedad y el horario hábil (leve fuera de horario: no avisa; media y grave: siempre);
  - pedir el aviso al servidor y esperar el plazo configurado;
  - consultar el estado de la alerta y pasar al siguiente contacto;
  - repetir la lista una vez e informar `SIN_RESPUESTA`;
  - detenerse al recibir por FCM "alerta confirmada" o "alerta resuelta".

  Los temporizadores deben sobrevivir a un reinicio del reloj.
- Repositorio de configuración versionada (umbrales, contactos sin números, plazos, horario hábil y feriados), aplicada aunque no haya conexión.
- Canal de comunicación detrás de una interfaz (LTE o Wi-Fi).
- Cola local con reintentos y pantalla "No se pudo avisar".
- Pantalla "Alerta recibida por [contacto]" al llegar el aviso por FCM.
- Respuesta al simulacro ("PRUEBA").
- Estado persistente en Room que sobreviva a reinicios.
- Sincronización cada 2 horas o al 15 % de batería y buffer local de 14 días.
- Pruebas unitarias del motor de reglas y del escalamiento, y contadores de eventos descartados.

No incluye llamadas, SMS, botón SOS, presión arterial ni zona segura.

## Anexo: contrato de mensajes (borrador)

Propuesta de partida para la tarea 0.4. **El contrato ya escrito está en [api-reloj.md](api-reloj.md) y [api-reloj.yaml](api-reloj.yaml) (25-10-2026) y reemplaza a esta tabla.** Debe validarse con quien desarrolle el reloj. Todo va por HTTPS con el token del reloj, salvo los avisos por FCM y el webhook de Meta.

| Mensaje | Cuándo | Contenido principal | Respuesta |
|---|---|---|---|
| Sincronización (reloj → servidor) | Cada 2 h o al 15 % de batería | `event_id`, batería, reloj puesto, tipo de conexión, versión de la app y de configuración, lecturas con su hora original (UTC), contadores de eventos descartados | Confirmación y aviso si hay una configuración nueva |
| Alerta (reloj → servidor) | Al terminar la ventana de 30 s sin cancelación | `event_id`, tipo, gravedad, etapa, evidencia de lecturas, versión de configuración | Confirmación con el identificador de la alerta. El reintento con el mismo `event_id` no duplica |
| Cancelación por el paciente (reloj → servidor) | Si el paciente cancela dentro de los 30 s | `event_id`, tipo, gravedad, hora | Confirmación (solo para métricas) |
| Solicitud de aviso (reloj → servidor) | Cada vez que el reloj avisa a un contacto | Identificador de la solicitud, alerta, contacto, canal, ronda | Enviado, o rechazado (contacto sin opt-in, inactivo o de otro paciente) para que el reloj pase al siguiente. Repetir la solicitud no envía dos veces |
| Estado de la alerta (reloj → servidor) | Antes de pasar al siguiente contacto | Identificador de la alerta | Estado y, si alguien confirmó, quién |
| Fin del escalamiento (reloj → servidor) | Al terminar la lista dos veces sin confirmación | Identificador de la alerta | Confirmación; la alerta pasa a `SIN_RESPUESTA` |
| Configuración (servidor → reloj) | En la sincronización, solo si cambió (o tras un aviso FCM de configuración nueva) | Umbrales, reglas de tendencia, GPS sí o no, módulos activos, modo conservador, contactos (identificador, nombre para mostrar, prioridad, horario y canales, sin números ni correos), plazo de espera, horario hábil, feriados, versión | La configuración o "sin cambios" |
| Webhook de WhatsApp (Meta → servidor) | Al responder un contacto o cambiar el estado de un mensaje | Botón pulsado o estado de entrega, con firma de Meta | 200 OK |
| Avisos por FCM (servidor → reloj) | Al confirmarse o resolverse una alerta, al haber configuración nueva o al pedir un simulacro | Acción e identificador, sin datos de salud | El reloj informa la recepción en su siguiente mensaje |

Estados de la alerta en el servidor: `RECIBIDA`, `AVISANDO_CONTACTO`, `CONFIRMADA`, `SIN_RESPUESTA`, `RESUELTA`, `PENDIENTE_DIA_HABIL`, `CANCELADA` (cancelada por el paciente).

## Riesgos y puntos a vigilar

- Sin botón de pánico: una persona consciente que se siente mal, pero no se cae ni tiene lecturas anormales, no puede pedir ayuda. Comunicarlo al PRLAC y dejarlo en el registro de riesgos.
- Cobertura LTE: en zonas sin señal (dentro o fuera de casa) la alerta queda en la cola del reloj hasta recuperar la señal. Verificar la señal en cada casa al instalar (5.6.1).
- Reloj sin señal, apagado o con el plan de datos vencido: el sistema queda ciego. Redes de seguridad: el silencio de datos de 4 horas y el aviso de plan por vencer en el inventario (3.8).
- El servidor municipal es la ruta crítica: aunque el reloj escala, cada WhatsApp lo envía el servidor. Si cae él, internet de la municipalidad o la API de Meta, no salen avisos. Mitigar con UPS, monitoreo externo y protocolo de soporte con TI.
- Escalamiento en el reloj: si el reloj se apaga o pierde la señal a mitad de un escalamiento, nadie sigue avisando. El servidor vigila las alertas abiertas sin noticias del reloj y avisa a los profesionales (2.10.6).
- Batería: el LTE consume más que el Wi-Fi. Medirlo en la prueba de factibilidad antes de comprometer la sincronización cada 2 horas.
- Costo: cada reloj necesita un plan de datos mensual, un costo fijo que antes no existía.
- Nadie llama automáticamente al SAMU. Depende de que un contacto responda y llame al 131.
- WhatsApp de noche: un mensaje se puede pasar por alto más fácilmente que una llamada. Definir contactos nocturnos con cuidado.
- Aprobación de Meta: la verificación del negocio y de las plantillas puede tardar. No dejarlo para el final.
- Opt-in: sin aceptación previa del contacto, no se le pueden enviar mensajes.
- Protección de datos: WhatsApp es un tercero (Meta). Mensajes sin datos clínicos y consentimiento informado actualizado.
- Datos del SDK de Samsung: son de bienestar, no de diagnóstico. Los reportes son apoyo para el profesional.
- Samsung Partner Program: sin aprobación, la app solo puede instalarse en modo desarrollador.
- FCM no es instantáneo: puede tardar según la red y el ahorro de energía. Si "alerta confirmada" se atrasa, el reloj podría avisar a otro contacto innecesariamente; se mitiga con la consulta de estado antes de cada salto (2.10.3).
- Falsos negativos: si una regla del reloj falla, la alerta no sale. Se mitiga con pruebas rigurosas, modo conservador, contadores de eventos descartados y la red de seguridad del servidor (2.7).
- 1.3 (login): la seguridad la implementa el propio equipo; la estimación podría subir.
- 5.1: depende de que llegue el reloj y de que el servidor municipal sea accesible desde internet.
- Feriados: si la lista no se actualiza cada año, una leve en feriado se trataría como día hábil.
- No incluye la validación clínica de umbrales y plazos con profesionales de salud.
- En pruebas se usan solo datos inventados y contactos de prueba.
## Preguntas abiertas

- [ ] Titular de la cuenta Meta Business y número dedicado (subtarea 0.7.1).
- [ ] Contacto en TI municipal y fecha de la reunión (tarea 0.8).
- [ ] Qué cuenta de correo institucional se usará y quién la administra (subtarea 2.4.2).
- [ ] Plazo de espera por contacto antes de pasar al siguiente con WhatsApp (a acordar con el PRLAC y el equipo clínico).
- [ ] Plazo sin noticias del reloj, durante un escalamiento, antes de avisar a los profesionales (2.10.6).
- [ ] Cuántos pacientes tendrá el piloto (para estimar la instalación en terreno).
- [ ] Modelo exacto del reloj con LTE, operador y plan de datos (costo mensual por reloj).
- [x] Estructura Maven multi-módulo: confirmada (domain, application, adapters, app; subtarea 0.3.3).
- [ ] Biblioteca para el segundo factor (TOTP).
- [ ] Si las 20 a 30 horas semanales incluyen también el desarrollo de la app del reloj.
- [ ] Quién desarrolla el reloj y cuándo necesita el contrato de la API (subtarea 0.4.8).
- [ ] Si los umbrales del Anexo B, las reglas de tendencia y los plazos fueron definidos o validados por un profesional de salud.
 
