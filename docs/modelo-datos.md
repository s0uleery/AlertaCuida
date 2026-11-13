# Modelo de datos de AlertaCuida

Tarea 0.4 del [cronograma](cronograma.md). El diagrama está en [`modelo-datos.dbml`](modelo-datos.dbml); para verlo, pegar el archivo en [dbdiagram.io](https://dbdiagram.io). Este documento explica las decisiones. Los nombres definitivos de tablas y columnas se fijan en las migraciones (1.1).

**Diseño (versión 2, 20 de octubre de 2026):** el reloj tiene LTE y gestiona los contactos y el escalamiento. Elige a quién avisar y cuándo, y le pide cada aviso al servidor. El servidor envía el WhatsApp o el correo, recibe la respuesta del contacto por webhook, avisa al reloj por FCM y registra todo. El equipo PRLAC atiende las alertas desde el dashboard.

## Principios

- **Normalizado hasta FNBC.** Cada dato se guarda en un solo lugar. Hay una sola excepción, controlada y explicada en [Normalización](#normalización). **Lo que se puede calcular no se guarda**: el estado de la alerta, la edad, el último acceso, el bloqueo por intentos fallidos, la configuración pendiente, los reportes y los hallazgos se obtienen con consultas.
- **Sin tablas sueltas.** Todas las tablas se relacionan con otra (ver [Relaciones](#relaciones)).
- **Identidad separada de salud (0.4.2).** Los datos personales están en `PACIENTE` y `CONTACTO`, cifrados. Los datos de salud van en `FICHA_PACIENTE`, `CONDICION_PACIENTE`, `CONFIGURACION` y `UMBRAL`.
- **Dónde va cada regla:**
  - si se puede comprobar mirando **una sola fila**, la hace cumplir la base (claves foráneas, `unique`, `CHECK`, `EXCLUDE`, índices únicos parciales);
  - si **cruza tablas**, la valida el backend y queda anotada en la nota de la tabla ("lo valida el backend"). Así no hay triggers que mantener.
- **El historial no se pierde.** Las asignaciones de reloj, las configuraciones y los umbrales se cierran o se agregan, no se sobrescriben. Los usuarios y los contactos se desactivan, no se borran.
- **Idempotencia.** Lo que puede llegar dos veces (lotes, lecturas reenviadas, alertas, solicitudes de aviso, webhooks de Meta) tiene una clave única.
- **Sin `jsonb` en la configuración clínica.** Cada opción es una columna (1FN). La lógica de las reglas (tendencia, matriz de riesgo) es del backend y del reloj, no un dato del paciente.

## Convenciones

- Claves UUID (0.4.2). Excepciones:
  - los catálogos (`TIPO_CONTACTO`, `DESTINO_DERIVACION`, `ROL`) usan un código de texto;
  - `CONFIGURACION`, `UMBRAL`, `EVENTO_DESCARTADO` y `PERMISO_ROL` usan claves naturales compuestas.
- Fechas y horas en `timestamptz` (UTC); el dashboard las muestra en `America/Santiago`.
- Las columnas marcadas "cifrado" guardan datos personales cifrados (1.6.3). Las que hay que buscar tienen además un hash (`rut_hash`, `whatsapp_hash`, `correo_hash`), que el backend calcula sobre el dato ya normalizado.
- Tablas en MAYÚSCULAS y claves `id_tabla`.
- Los `Enum` se dibujan como `Enum`, pero en las migraciones van como `varchar` con `CHECK` (los `ENUM` de PostgreSQL no permiten quitar ni renombrar valores).
- **Fuera de la base**, en la configuración del backend (`application.properties`, y el horario hábil y los feriados en `calendario.yml`): umbrales base, matriz de riesgo FC × SpO2 (con su versión), horario hábil, feriados, plazos y la política de bloqueo de sesión (intentos y minutos). Son valores del sistema, no datos de un paciente. Cambiarlos requiere reiniciar el backend. La regla de enrutamiento por gravedad está fija en el código (`ReglaEnrutamiento`).

## Tablas (30)

| Área | Tablas |
|---|---|
| 1. Paciente (identidad) | `PACIENTE` |
| 2. Salud y documentos | `FICHA_PACIENTE`, `CONDICION_PACIENTE`, `DOCUMENTO` |
| 3. Contactos | `TIPO_CONTACTO`, `CONTACTO`, `CONTACTO_PACIENTE` |
| 4. Configuración clínica | `CONFIGURACION`, `UMBRAL`, `ZONA_SEGURA` |
| 5. Relojes y telemetría | `SMARTWATCH`, `ASIGNACION_RELOJ`, `SINCRONIZACION`, `EVENTO_DESCARTADO`, `REGISTRO_VITAL`, `UBICACION` |
| 6. Alertas | `ALERTA`, `DISCREPANCIA_ALERTA`, `ATENCION_ALERTA`, `REGISTRO_INTENTO`, `AVISO_FCM` |
| 7. Seguimiento | `DESTINO_DERIVACION`, `SEGUIMIENTO` |
| 8. Personal, roles y permisos | `ROL`, `PERMISO_ROL`, `USUARIO`, `CODIGO_RESPALDO`, `TOKEN_SESION`, `TOKEN_ACCION`, `REGISTRO_AUDITORIA` |

### 1. Paciente

- **`PACIENTE`**: solo identidad. RUN, nombres, apellidos, fecha de nacimiento y dirección (cifrados), un `alias` para los mensajes (sin datos clínicos), sexo, nivel de dependencia (vacío = por evaluar), fecha del consentimiento (5.5.1), ingreso y baja.
  - **Activo = sin `fecha_baja`.** La baja reemplaza al borrado durante el piloto.
  - **La edad no se guarda**: la calcula el backend desde la fecha de nacimiento.
  - `rut` y `rut_hash` son obligatorios: sin el RUN cifrado, el hash no se puede revertir y se perdería el dato.
  - Búsqueda por RUN con `rut_hash`. Por nombre, descifrando en el backend (son pocos pacientes).

### 2. Salud y documentos

- **`FICHA_PACIENTE`** (uno a uno con `PACIENTE`): inscripción en el Registro Nacional de la Discapacidad y notas sociales (cifradas). Son datos que no cambian la configuración del reloj.
- **`CONDICION_PACIENTE`**: una condición por fila (1FN), con su `tipo` y su descripción cifrada:
  - `ENFERMEDAD`: por ejemplo, EPOC, hipertensión o diabetes;
  - `SITUACION`: por ejemplo, discapacidad visual, movilidad reducida o vive sola.

  Van en la misma tabla porque tienen las mismas columnas: se distinguen por tipo, no por tabla. Dos tablas iguales obligarían a unirlas cada vez que la ficha muestre todo. Si el paciente está inscrito en el Registro Nacional de la Discapacidad, eso va en `FICHA_PACIENTE`.
- **`DOCUMENTO`**: informe sociosanitario, consentimiento u otro. El archivo va cifrado en el disco del servidor; la base guarda la ruta (única), el hash SHA-256 para detectar alteraciones, la fecha y quién lo subió.

### 3. Contactos

- **`TIPO_CONTACTO`**: catálogo (familia, vecino, PRLAC, seguridad ciudadana, institución, otro). Dice **quién es** la persona respecto del paciente. Ser cuidador es un **rol** (`es_cuidador_principal`), no un tipo.
- **`CONTACTO`**: la **persona** o institución, guardada una sola vez aunque sea contacto de varios pacientes (por ejemplo, Seguridad Ciudadana).
  - Solo nombre y medio de contacto (minimización): WhatsApp y correo, cifrados, al menos uno de los dos.
  - El opt-in de WhatsApp es de la persona, porque Meta lo registra por número.
  - `whatsapp_hash` y `correo_hash` son únicos: un número o un correo es una sola persona. Así se la encuentra cuando Meta informa un número (por ejemplo, la baja del opt-in).
  - **Contacto interno (V11):** una cuenta del equipo PRLAC que recibe avisos del servidor (correo por `recibe_avisos`, WhatsApp a la oficina y su correo de respaldo). Es solo el enlace a `USUARIO` (`id_usuario`, único): nombre, correo, teléfono y opt-in se leen de la cuenta, sin copiarlos. El externo es la persona de la lista de un paciente, como antes.
  - Un contacto interno no entra en la lista de ningún paciente, así que el reloj nunca lo recorre, y un contacto no cambia de interno a externo ni al revés. Lo impiden dos disparadores que responden como un `CHECK`.
- **`CONTACTO_PACIENTE`**: la lista de contactos de cada paciente. Guarda lo que depende del vínculo:
  - tipo (alguien puede ser cuidadora de un paciente y vecina de otro) y parentesco (solo si es `FAMILIA`);
  - si es el cuidador principal (uno activo por paciente);
  - prioridad (única entre los activos del paciente);
  - horario de disponibilidad (si `hora_inicio > hora_fin`, cruza la medianoche; vacío = todo el día);
  - si está activo (se desactiva, no se borra, porque sus intentos quedan en el historial).
- Al reloj le llega la lista **sin números ni correos**: el reloj pide el aviso por identificador y solo el servidor conoce los datos.
- **El último cambio no se guarda** en estas tablas: sale de `REGISTRO_AUDITORIA`. La configuración pendiente del reloj se detecta por hash (ver abajo).

### 4. Configuración clínica

- **`CONFIGURACION`**: historial de los módulos del paciente. Cada fila rige desde `vigente_desde` hasta la siguiente; **la vigente es la más reciente**. Cada cambio agrega una fila y las anteriores no se modifican.
  - Un módulo por columna (caídas, FC, SpO2, inactividad, tendencia, zona segura, botón de ayuda, GPS), además del modo conservador, los minutos de espera entre contactos (vacío = valor base) y el motivo del cambio (cifrado). Son pocos y fijos, y cada uno depende de la clave completa.
  - El GPS ubica a la persona y, con `zona_segura_activa`, permite revisar la zona: `CHECK (NOT zona_segura_activa OR gps_activo)`. Que exista una zona guardada lo valida el backend, porque está en otra tabla.
  - El botón de ayuda viene activo por defecto (V9). Las filas anteriores a V9 quedan con el botón apagado, porque entonces no existía; cada paciente configurado recibió una versión nueva con el botón activo, auditada como cambio del sistema.
- **`ZONA_SEGURA`** (v8, migración V9): el círculo que el reloj revisa con su GPS, con **historial propio**, como el umbral: cambiar el radio no agrega una versión de `CONFIGURACION`.
  - Centro (latitud y longitud, **cifradas** como texto porque suelen ser el domicilio), radio en metros (el backend exige de 50 a 5000) y la gravedad de la alerta al salir (media por defecto). Todos dependen de la clave completa `(id_paciente, vigente_desde)`, así que cumple la FNBC.
  - Guardar una zona no la activa: el interruptor es `CONFIGURACION.zona_segura_activa`. Así se puede dejar definida y apagada sin perderla.
  - No va dentro de `CONFIGURACION` para no repetir el círculo en cada versión de los módulos, ni dejar columnas vacías en quienes no tienen zona.
- **`UMBRAL`**: rangos personalizados por variable (FC, SpO2), con **historial propio**. Cambiar el GPS no duplica umbrales, y cambiar un umbral no duplica la configuración.
  - Cada fila define cuatro límites: `grave_bajo < normal_min < normal_max < grave_alto`. Cada variable queda NORMAL, LEVE o GRAVE, y la matriz de riesgo del backend cruza FC y SpO2 para dar la severidad.
  - **Una fila va completa o vacía; no se mezcla con el umbral base:**
    - vacía (las cuatro columnas) o sin fila vigente = rige el umbral base del backend. Así se vuelve al predeterminado sin borrar historial;
    - completa = las cuatro columnas. SpO2 no tiene límite alto: `normal_max` y `grave_alto` van siempre vacíos, y `normal_min` es de 100 % como máximo.
  - Agregar una variable (por ejemplo, presión arterial) es un valor nuevo en `variable_vital`, no columnas nuevas.
- Quién hizo cada cambio queda en `REGISTRO_AUDITORIA` (1.5.2).

### 5. Relojes y telemetría

- **`SMARTWATCH`**: identificador del reloj, modelo, SIM LTE (ICCID, número y vencimiento del plan, 3.8.2), token de FCM y estado físico (`OPERATIVO`, `EN_REPARACION`, `RETIRADO`). Si está asignado se sabe por `ASIGNACION_RELOJ`.
- **`ASIGNACION_RELOJ`**: vínculo reloj-paciente con historial (1.2.4) y el hash del token del reloj (rol `DEVICE`).
  - **Todo lo que llega del reloj cuelga de la asignación**, que da el reloj y el paciente con una sola relación.
  - El token vale mientras la asignación está vigente: cerrarla lo revoca. Para cambiar el token se cierra la asignación y se abre otra.
  - **Sin superposiciones en el tiempo**, ni vigentes ni cerradas, ni por reloj ni por paciente (1.2.5): dos restricciones `EXCLUDE`. Cerrar a las 12:00 y abrir otra a las 12:00 no se superpone.
- **`SINCRONIZACION`**: el lote cada 2 horas (o antes si la batería baja de 15 %) con el estado del reloj: batería, si está puesto, minutos puesto desde el lote anterior (uso del reloj en reportes), LTE o Wi-Fi, versión de la app y **hash de la configuración** que tiene aplicada. La última sincronización da el estado actual (3.4.1, 3.8.1).
- **`EVENTO_DESCARTADO`**: cuántos eventos de cada tipo descartó el reloj en el lote, una fila por tipo (1FN). Sirve a las métricas de validación (4.7). No admite `SILENCIO_DATOS`, porque ese evento lo detecta el servidor.
- **`REGISTRO_VITAL`** y **`UBICACION`**: las lecturas de FC y SpO2 (cada 30 min) y las posiciones GPS. El reloj guarda 14 días y puede reenviar historial: una lectura reenviada en otro lote no se duplica, gracias al único `(id_asignacion, tipo, fecha_hora_medicion)`. Por eso llevan `id_asignacion` (ver [Normalización](#normalización)).
- **Configuración pendiente (1.7.3).** El backend arma el documento que envía al reloj (datos de la base + `application.yml`) y calcula su hash. Si el hash del último lote no coincide, el reloj tiene configuración pendiente. Una sola comparación cubre los cambios en la base y en el yml.
  - El hash no guarda el documento. Lo que usó una alerta queda en su `evidencia` (umbrales aplicados, versión de la matriz, celda que dio la severidad) y en sus intentos (a quién se avisó).

### 6. Alertas

- **Tipos de evento.**
  - Clínicos, que escalan a los contactos: caída, FC, SpO2, FC+SpO2, tendencia, inactividad, salida de la zona segura (con la gravedad de su zona) y botón de ayuda (siempre leve; avisa primero a los `VECINO`, regla `alerta_boton_leve`).
  - Técnicos, que avisan a todos los coordinadores (por WhatsApp, en horario laboral) y por correo a las demás cuentas con `recibe_avisos` (apagado por defecto desde V10): silencio de datos (sin señal, reloj apagado o plan vencido), batería baja, reloj no puesto y exceso de alertas (`EXCESO_ALERTAS`: desde la 10.ª alerta del mismo reloj en 60 minutos, posible falla; leve).
  - El servidor solo crea alertas técnicas y simulacros, y el silencio de datos solo lo crea el servidor (`CHECK`).
- **Gravedad y enrutamiento.** Hay tres niveles. La regla está fija en el código (`ReglaEnrutamiento`); el horario de cada contacto se respeta siempre, también en horario hábil:

  | Gravedad | En horario hábil | Fuera del horario hábil |
  |---|---|---|
  | `LEVE` | Avisa a los contactos | No avisa: espera al día hábil (`espera_dia_habil`) |
  | `MEDIA` | Avisa a los contactos | Avisa a los contactos disponibles a esa hora |
  | `GRAVE` | Avisa a los contactos | Avisa a los contactos disponibles a esa hora |

- **`ALERTA`**: lo que pasó y lo que hizo el reloj. **Es la evidencia del evento**, y la escriben solo el reloj (o el servidor, si la alerta es suya):
  - `numero`: folio legible para el equipo (#142);
  - origen, tipo, `severidad` (la gravedad **con que llegó**), evidencia, hash de la configuración (solo las del reloj), si es simulacro;
  - `fecha_hora_agravamiento`: la caída leve o media que sube a grave por inmovilidad (2.1.3). La gravedad actual se calcula;
  - detección, fin de la ventana de 30 s y recepción (recepción menos detección = retraso por cobertura LTE);
  - `espera_dia_habil` (solo en las leves), cancelación por el paciente (1.4.4) y fin de la lista sin respuesta.

  Después de crearla, el reloj solo completa las fechas que informa más tarde (agravamiento, cancelación, sin respuesta); nada se sobrescribe. Se controla con permisos de la base: el usuario del dashboard no tiene `UPDATE` sobre `ALERTA`, y el de la recepción del reloj solo sobre esas tres columnas.
- **`ATENCION_ALERTA`** (uno a uno con `ALERTA`): lo que hace el equipo PRLAC. La fila se crea cuando alguien asume el caso o cuando se cierra; **sin fila = sin asignar**.
  - Responsable y hora de toma; cierre, quién la resolvió (vacío si la cerró el sistema, por ejemplo, porque volvieron los datos), clasificación (`POSITIVA` o `FALSA_ALARMA`, solo en las clínicas) y observaciones (cifradas).
  - Si es `POSITIVA`, se abre el seguimiento, que exige responsable y se cierra con `fecha_cierre_seguimiento`.
  - **Asumir sin carreras:** `INSERT ... ON CONFLICT (id_alerta) DO UPDATE ... WHERE fecha_hora_toma IS NULL AND fecha_hora_cierre IS NULL`. Si no afecta filas, otra persona ya la tomó o ya está cerrada. Las reasignaciones quedan en la auditoría.
  - Separar la atención de la alerta evita mezclar dos autores en una fila: la evidencia del reloj no se toca cuando el equipo trabaja.
- **El estado no se guarda; se calcula** con la vista `ALERTA_ESTADO`, que da dos estados:

  | Estado de contacto (lo que hace el reloj) | Cuándo |
  |---|---|
  | `CANCELADA` | Tiene fecha de cancelación |
  | `RESUELTA` | La atención tiene fecha de cierre |
  | `CONFIRMADA` | Un contacto respondió "Voy en camino" |
  | `SIN_RESPUESTA` | El reloj terminó la lista dos veces |
  | `AVISANDO_CONTACTO` | Hay al menos un aviso |
  | `PENDIENTE_DIA_HABIL` | `espera_dia_habil` (bandeja 3.7) |
  | `RECIBIDA` | Ninguno de los anteriores |

  | Estado de atención (lo que hace el equipo) | Cuándo |
  |---|---|
  | `EN_SEGUIMIENTO` | `POSITIVA` sin `fecha_cierre_seguimiento` |
  | `RESUELTA` | Tiene fecha de cierre |
  | `EN_ATENCION` | Tiene fecha de toma |
  | `SIN_ASIGNAR` | Sin fila en `ATENCION_ALERTA` |

  La vista también da la gravedad actual: `GRAVE` si se agravó; si no, la inicial. "Derivada" no es un estado: se ve en `SEGUIMIENTO`.
- **Una alerta del servidor abierta por reloj y tipo (2.2.5).** "Abierta" depende de `ATENCION_ALERTA`, así que no puede ser un índice parcial. El job del servidor la valida al crearla, dentro de una transacción con `pg_advisory_xact_lock(hashtextextended(id_asignacion::text || ':' || tipo_evento, 0))`. Las del reloj nunca se rechazan (2.1).
- **`DISCREPANCIA_ALERTA`** (2.1.2, V10): lo que no calzó entre una alerta del reloj y la configuración vigente al recibirla: otra versión, módulo apagado, gravedad distinta de la configurada (tendencia o zona segura) o lecturas que no superan los umbrales. La alerta se acepta igual. Una fila por código (1FN), con un detalle legible. **Se guarda y no se calcula** porque los valores base del backend no tienen historial: después no se podría saber qué regía. Las cancelaciones no se revisan.
- **Agravamiento** (2.1.3): `fecha_hora_agravamiento` y `segundos_inmovilidad_agravamiento` (solo con la fecha) se completan una vez; repetirlo no cambia la hora.
- **`REGISTRO_INTENTO`**: cada aviso que el reloj pidió y el servidor envió a un contacto, con canal, ronda (1 o 2), horas de solicitud, envío, entrega, lectura (solo WhatsApp) o falla, y la respuesta (botón de WhatsApp o enlace del correo). El estado de entrega es la fecha más avanzada. También los avisos del servidor al equipo, con su propósito: `AVISO` o `VIGILANTE` (la alerta no avanza porque el reloj dejó de pedir avisos).
  - Desde V11 también guarda los avisos que decide el servidor para el equipo: van a un contacto interno (`id_contacto_interno`), sin ronda. Cada aviso va a un vínculo o a un contacto interno, nunca a los dos. Los avisos al equipo no cuentan para `AVISANDO_CONTACTO`, y solo un contacto del paciente puede responder "Voy en camino".
  - Es también la bandeja de salida del correo: `fecha_hora_proximo_envio` marca un envío pendiente y desde cuándo puede salir; `reintentos` cuenta los reintentos hechos. Agotados, queda con `fecha_hora_fallo` y `motivo_fallo`.
  - `event_id` lo genera el reloj al pedir el aviso: si reintenta, no se envía dos veces (2.10.2).
  - Además, un aviso por contacto, canal y ronda (`unique`), aunque el reloj genere otro `event_id` tras reiniciarse.
  - `id_externo` es el identificador del mensaje en Meta o en el correo: un webhook repetido no se procesa dos veces.
- **`AVISO_FCM`**: los avisos al reloj sobre una alerta (2.3): `ALERTA_CONFIRMADA`, `ALERTA_RESUELTA` y `SIMULACRO`. Uno de cada tipo por alerta; los reintentos suman en `intentos`. El aviso de configuración nueva no se guarda, porque la pendiente se calcula.

### 7. Seguimiento

- **`DESTINO_DERIVACION`**: catálogo (CESFAM, SAMU, hospital, otro).
- **`SEGUIMIENTO`**: notas y acciones del equipo sobre un caso (llamada, visita, derivación con su destino, nota). El "historial del caso" del dashboard no se guarda: es una vista que une las fechas de `ALERTA`, `REGISTRO_INTENTO`, `AVISO_FCM` y `SEGUIMIENTO`.

### 8. Personal, roles y permisos

- **`ROL`** y **`PERMISO_ROL`**: cada rol tiene permisos por módulo (alertas, usuarios, reportes, administración) y acción (ver, crear, editar, eliminar, exportar). **La fila existe = el rol tiene el permiso.** Agregar una acción no cambia la tabla. Módulos y acciones son `Enum` porque cada uno corresponde a una regla en Spring Security. El rol `ADMINISTRADOR` es de sistema (`es_sistema`): sus permisos no se editan, para no dejar a nadie sin acceso.
- **`USUARIO`**: lo crea el administrador. El usuario recibe un enlace de activación y define su contraseña, y ahí queda activo.
  - Inactivo sin contraseña = pendiente de activar; inactivo con contraseña = desactivado. Los usuarios no se borran.
  - RUN cifrado con `rut_hash` para el inicio de sesión, correo (único y en minúsculas), TOTP, `recibe_avisos` y `recibe_resumen_diario`.
  - Teléfono (cifrado, número de WhatsApp normalizado) y opt-in de WhatsApp (`fecha_opt_in`, `fecha_revocacion_opt_in`, como en `CONTACTO`). El aviso a la oficina PRLAC llega por WhatsApp a todas las cuentas activas con rol `COORDINADOR` y opt-in vigente, solo en horario laboral; si falla, por correo. El teléfono es obligatorio para ese rol: lo valida el backend al crear la cuenta, porque una regla en la base rechazaría las cuentas que ya existían sin teléfono.
  - **El último acceso y el bloqueo no se guardan**: salen de `REGISTRO_AUDITORIA`. Está bloqueado si tiene N `LOGIN_FALLIDO` en los últimos M minutos sin un `LOGIN` posterior (N y M en el yml).
- **`CODIGO_RESPALDO`** (1.3.5), **`TOKEN_SESION`** (renovación del dashboard, 1.3.4) y **`TOKEN_ACCION`** (enlaces de activación y recuperación por correo, de un solo uso): tablas propias porque son varios por usuario y tienen ciclos de vida distintos.
- **`REGISTRO_AUDITORIA`**: quién leyó o cambió qué y cuándo, sin datos personales (1.6, 1.5.2).
  - `id_entidad` no tiene clave foránea a propósito, para que el registro sobreviva al borrado de lo auditado. Es texto porque los catálogos usan código.
  - En las claves compuestas, `id_entidad` une sus partes con `|` en el orden de la clave: `CONFIGURACION` "id_paciente|vigente_desde", `UMBRAL` "id_paciente|variable|vigente_desde", `ZONA_SEGURA` "id_paciente|vigente_desde", `PERMISO_ROL` "codigo_rol|modulo|accion" (`vigente_desde` en ISO 8601 UTC).
  - En `LEER`, `id_entidad` es el id del paciente cuya información se consultó, o `LISTA`; `entidad` dice qué parte se leyó. En las demás acciones es el id del objeto cambiado. Por eso, el historial de un objeto específico no incluye sus lecturas: están en el historial del paciente.
  - Las frases del dashboard ("Cambio de dependencia de …") se arman al mostrar.

### Lo que se calcula con consultas

| Dato | Cómo se obtiene |
|---|---|
| Estado de contacto y de atención de la alerta | Vista `ALERTA_ESTADO` |
| Gravedad actual de la alerta | `GRAVE` si tiene `fecha_hora_agravamiento`; si no, `severidad` (vista `ALERTA_ESTADO`) |
| Estado del paciente | `fecha_baja IS NULL` |
| Edad | El backend, al descifrar `fecha_nacimiento` (va cifrada, por eso es texto y no `date`) |
| Configuración vigente | Fila de `CONFIGURACION` con el mayor `vigente_desde` |
| Umbral vigente (o el que regía en una fecha) | Fila de `UMBRAL` de esa variable con el mayor `vigente_desde` que no supere la fecha; vacía o sin fila = umbral base |
| Zona segura vigente | Fila de `ZONA_SEGURA` con el mayor `vigente_desde`; rige solo con `zona_segura_activa` |
| Configuración pendiente del reloj | El hash del último lote no coincide con el que calcula el backend |
| Validez del token del reloj | La asignación está vigente (`fecha_fin` vacía) |
| Relojes libres | `OPERATIVO` y sin asignación vigente |
| Estado actual del reloj | Última `SINCRONIZACION` |
| Estado de entrega de un mensaje | Fecha más avanzada del intento |
| Último acceso y bloqueo de un usuario | `LOGIN` y `LOGIN_FALLIDO` en `REGISTRO_AUDITORIA` |
| Último cambio de un contacto | `REGISTRO_AUDITORIA` |
| Historial del caso | Fechas de `ALERTA`, `REGISTRO_INTENTO`, `AVISO_FCM` y `SEGUIMIENTO` |
| Reportes diarios y semanales (3.6) | Promedios, máximos y mínimos de `REGISTRO_VITAL`; uso del reloj con `minutos_puesto` |
| Hallazgos de la red de seguridad (2.7) | Lecturas fuera de umbral sin alerta del reloj en la misma ventana |

## Relaciones

35 relaciones, todas 1 a muchos (tres de ellas uno a uno):

| Tabla padre | Tablas hijas |
|---|---|
| `PACIENTE` | `FICHA_PACIENTE` (1 a 1), `CONDICION_PACIENTE`, `DOCUMENTO`, `CONFIGURACION`, `UMBRAL`, `ZONA_SEGURA`, `CONTACTO_PACIENTE`, `ASIGNACION_RELOJ` |
| `CONTACTO` | `CONTACTO_PACIENTE` (externo), `REGISTRO_INTENTO` (interno) |
| `TIPO_CONTACTO` | `CONTACTO_PACIENTE` |
| `SMARTWATCH` | `ASIGNACION_RELOJ` |
| `ASIGNACION_RELOJ` | `SINCRONIZACION`, `ALERTA` |
| `SINCRONIZACION` | `EVENTO_DESCARTADO`, `REGISTRO_VITAL`, `UBICACION` |
| `ALERTA` | `ATENCION_ALERTA` (1 a 1), `DISCREPANCIA_ALERTA`, `REGISTRO_INTENTO`, `AVISO_FCM`, `SEGUIMIENTO` |
| `CONTACTO_PACIENTE` | `REGISTRO_INTENTO` |
| `DESTINO_DERIVACION` | `SEGUIMIENTO` |
| `ROL` | `USUARIO`, `PERMISO_ROL` |
| `USUARIO` | `CONTACTO` (interno, 1 a 1), `DOCUMENTO`, `ATENCION_ALERTA` (responsable), `ATENCION_ALERTA` (resolutor), `SEGUIMIENTO`, `CODIGO_RESPALDO`, `TOKEN_SESION`, `TOKEN_ACCION`, `REGISTRO_AUDITORIA` |

- **Uno a uno con `>` en el DBML.** En DBML, `Ref: A.x - B.y` pone la clave foránea en `B`, al revés de lo que se quiere: el paciente dependería de su ficha y la alerta de su atención. Por eso se escriben con `>`. La clave primaria de la tabla hija (que es la misma del padre) ya garantiza que sean uno a uno.
- **Las dos relaciones de `ATENCION_ALERTA` con `USUARIO`** no se repiten: son dos hechos distintos (quién asumió el caso y quién lo cerró), que pueden ser personas diferentes.
- **`CONTACTO_PACIENTE`** es la tabla intermedia de una relación muchos a muchos.
- **`CONTACTO.id_usuario`** es uno a uno con `USUARIO` (columna única) y se escribe con `>` por la misma razón. No repite la relación de `ROL`: dice qué contacto representa a la cuenta, no qué permisos tiene.
- **`REGISTRO_INTENTO`** une una alerta con un vínculo contacto-paciente, y ambos llevan al mismo paciente por caminos distintos. Es propio de una tabla que une dos cosas del mismo dueño. Que los dos caminos coincidan lo valida el backend.
- **`hash_configuracion`** no es clave foránea: se compara con el hash que calcula el backend, así que no crea otro camino.

## Normalización

- **1FN (valores atómicos, sin grupos repetidos).** Lo que se repite va en su propia tabla: condiciones, documentos, contactos, versiones, umbrales por variable, lecturas, contadores de descartes, intentos, seguimientos, permisos, códigos y tokens. Los módulos de la configuración son columnas, no un `jsonb`. Quedan dos columnas `jsonb`, ambas documentos que se guardan y se leen completos:
  - `ALERTA.evidencia`: foto de lo que causó la alerta;
  - `REGISTRO_AUDITORIA.detalle`: valores anteriores y nuevos, distintos según la tabla auditada.
- **2FN (sin dependencias de parte de la clave).** Claves compuestas:
  - `CONFIGURACION (id_paciente, vigente_desde)`: los módulos dependen de la versión completa;
  - `UMBRAL (id_paciente, variable, vigente_desde)`: cada límite depende de la variable y de la versión;
  - `EVENTO_DESCARTADO (id_sincronizacion, tipo_evento)`: la cantidad depende del lote y del tipo;
  - `PERMISO_ROL (codigo_rol, modulo, accion)`: toda la fila es la clave.
- **3FN (sin dependencias transitivas).** No se guardan el estado de la alerta, del paciente ni del seguimiento, la gravedad actual, la edad, el último acceso, el bloqueo, la fecha del último cambio, la configuración pendiente, los reportes ni los hallazgos.
- **FNBC.** Los datos que determinan a otros son claves o tienen `unique`: `rut_hash`, `whatsapp_hash`, `correo_hash`, `correo`, `identificador_reloj`, `iccid`, `numero_sim`, `hash_token_reloj`, `hash_token`, `event_id`, `id_externo`, `numero`, `ruta`.
  - `rut_hash`, `whatsapp_hash` y `correo_hash` se calculan a partir de un dato cifrado, pero son únicos (claves candidatas), así que cumplen la FNBC. Existen porque un dato cifrado no se puede buscar ni hacer único.

**La única excepción: `id_asignacion` en `REGISTRO_VITAL` y `UBICACION`.**
- Sale del lote (`id_sincronizacion → id_asignacion`), y `id_sincronizacion` no es clave de esas tablas. En sentido estricto, no cumple la 3FN ni la FNBC.
- Se guarda porque el reloj reenvía historial: el único `(id_asignacion, tipo, fecha_hora_medicion)` evita duplicar una lectura que llega en otro lote, y un índice no puede atravesar un join.
- **No se puede contradecir:** la clave foránea compuesta `(id_sincronizacion, id_asignacion)` apunta al único homónimo de `SINCRONIZACION`, así que la base rechaza una asignación distinta a la del lote. Es redundancia controlada, no una copia que pueda quedar desactualizada.

## Migraciones (1.1)

Las tablas las crea Flyway con las migraciones de [`adapters/src/main/resources/db/migration/`](../adapters/src/main/resources/db/migration/), que son la fuente de verdad de la base real (PostgreSQL 18). Los nombres van en minúsculas (`paciente`, `contacto_paciente`), sin comillas; el DBML los dibuja en MAYÚSCULAS.

| Migración | Contenido |
|---|---|
| `V1__personal_roles_y_auditoria` | `rol`, `permiso_rol`, `usuario`, códigos, tokens y auditoría (van primero porque otras tablas apuntan a `usuario`) |
| `V2__pacientes_salud_y_contactos` | `paciente`, `ficha_paciente`, `condicion_paciente`, `documento`, `tipo_contacto`, `contacto`, `contacto_paciente` |
| `V3__configuracion_clinica` | `configuracion`, `umbral` |
| `V4__relojes_y_telemetria` | Extensión `btree_gist`, `smartwatch`, `asignacion_reloj`, `sincronizacion`, `evento_descartado`, `registro_vital`, `ubicacion` |
| `V5__alertas_y_seguimiento` | `alerta`, `atencion_alerta`, `registro_intento`, `aviso_fcm`, `destino_derivacion`, `seguimiento` y la vista `alerta_estado` |
| `V6__catalogos_iniciales` | Roles (el `ADMINISTRADOR` con todos los permisos), tipos de contacto y destinos de derivación |
| `V7__auditoria_token_reutilizado` | Agrega la acción `TOKEN_REUTILIZADO` a `REGISTRO_AUDITORIA` |
| `V8__tendencia_activa_por_defecto` | Activa el módulo de tendencia por defecto y agrega una versión nueva, auditada, a quienes lo tenían apagado |
| `V9__zona_segura_boton_ayuda_y_optin_equipo` | Zona segura, botón de ayuda y opt-in de WhatsApp de las cuentas del equipo |
| `V10__discrepancias_agravamiento_y_avisos` | `discrepancia_alerta`, segundos de inmovilidad del agravamiento, `EXCESO_ALERTAS` y `recibe_avisos` apagado por defecto |
| `V11__correo_y_contacto_interno` | Contacto interno (`contacto.id_usuario`), avisos al equipo y reintentos en `registro_intento`, sus disparadores, y la vista `alerta_estado` sin contar los avisos al equipo |
| `V12__vigilante_del_escalamiento` | `registro_intento.fecha_hora_solicitud` (cuándo se pidió o encoló cada aviso) y `registro_intento.proposito` (`AVISO` o `VIGILANTE`), que entra en la unicidad de los avisos al equipo |

Una migración ya aplicada no se edita: los cambios van en una migración nueva (`V7__...`).

Resumen de lo que DBML no dibuja (está dentro de cada tabla en las migraciones y, para las pruebas del diseño, en [`pruebas-modelo-datos/reglas-extra.sql`](pruebas-modelo-datos/reglas-extra.sql)):

- **Índices únicos parciales:** `CONTACTO_PACIENTE (id_paciente, orden_prioridad) WHERE activo` y `CONTACTO_PACIENTE (id_paciente) WHERE es_cuidador_principal AND activo`.
- **Índices en claves foráneas.** PostgreSQL no los crea solo. Se indexa una clave foránea si su padre se borra en cascada sobre una tabla grande, o si se consulta por ella; si ya hay un índice o `unique` que empieza por esa columna, no hace falta otro. Se agregaron seis: `registro_vital (id_sincronizacion)`, `ubicacion (id_sincronizacion)`, `seguimiento (id_alerta, fecha_hora)`, `atencion_alerta (id_usuario_responsable)`, `contacto_paciente (id_contacto)` y `registro_intento (id_contacto_paciente)`. Las demás no se indexan: son catálogos o usuarios que no se borran, tokens que se buscan por su hash, o tablas con pocas filas por paciente.
- **`EXCLUDE`** (extensión `btree_gist`): `ASIGNACION_RELOJ` sin superposición de `tstzrange(fecha_inicio, fecha_fin)` por `id_dispositivo` y por `id_paciente`.
- **Clave diferida:** `REGISTRO_INTENTO.id_contacto_paciente` va `DEFERRABLE INITIALLY DEFERRED`. Sin esto, al borrar un paciente la cascada borra sus vínculos antes que los intentos de sus alertas, y PostgreSQL lo rechaza. Diferida, permite borrar al paciente completo, pero no un vínculo suelto que tiene intentos.
- **`CHECK`** (están en la nota de cada tabla del DBML):
  - fechas en orden: baja después del ingreso, fin de asignación después del inicio, tokens que vencen después de emitirse, cierre después de la toma, cierre del seguimiento después del cierre;
  - pares que van juntos: dato cifrado y su hash, horario de inicio y fin, respuesta y su hora, toma y responsable;
  - `UMBRAL` completo o vacío, en orden, y SpO2 sin límite alto y hasta 100 %;
  - `ALERTA`: el origen coherente con el tipo y con el hash, agravamiento solo de caídas no graves, espera del día hábil solo en leves, cancelada sin "sin respuesta";
  - `ATENCION_ALERTA`: sin filas vacías, clasificación solo al cerrar, `POSITIVA` con responsable;
  - rangos: batería 0–100, prioridad ≥ 1, cantidades e intentos ≥ 0, ronda 1 o 2;
  - `USUARIO`: activo solo con contraseña, correo en minúsculas;
  - `CONTACTO`: el externo con nombre y algún canal, el interno sin datos propios;
  - `REGISTRO_INTENTO`: un solo destino (vínculo o contacto interno), ronda solo con vínculo, respuesta solo de un vínculo, reintentos ≥ 0, y pendiente de envío solo si no salió ni falló.
- **Disparadores (V11)**, que responden con el código de un `CHECK`: un contacto interno no entra en `CONTACTO_PACIENTE`, un contacto no cambia de interno a externo, y `REGISTRO_INTENTO.id_contacto_interno` apunta a un contacto interno. Son reglas entre tablas que un `CHECK` no puede revisar.
- **Bandeja de salida:** índice parcial `registro_intento (fecha_hora_proximo_envio) WHERE fecha_hora_proximo_envio IS NOT NULL`, y único `(id_alerta, id_contacto_interno, canal)` para no repetir un aviso al equipo.
- **Vista** `ALERTA_ESTADO`, con las dos tablas de estados de arriba.
- **Permisos de la base:** el usuario del dashboard no tiene `UPDATE` sobre `ALERTA`; el de la recepción del reloj, solo `UPDATE (fecha_hora_agravamiento, fecha_hora_cancelacion, fecha_hora_sin_respuesta)`.
- **UUID** generados con `gen_random_uuid()`, salvo los `event_id`, que los genera el reloj (o el servidor, en sus alertas).

### Validaciones en el backend

Reglas que cruzan tablas. Están anotadas en la nota de cada tabla y probadas (ℹ️ en el informe):

- solo se asignan relojes `OPERATIVO`, a pacientes con consentimiento y sin baja; al dar de baja a un paciente se cierra su asignación;
- una sola alerta del servidor abierta por reloj y tipo (con el bloqueo consultivo de arriba);
- una alerta cancelada no tiene atención ni avisos a contactos; las técnicas no tienen clasificación;
- el vínculo de un intento es del paciente de la alerta, la persona tiene el canal pedido y, en WhatsApp, opt-in vigente;
- no se agrega seguimiento después de su cierre;
- los permisos de un rol de sistema no se editan;
- al desactivar un usuario, sus enlaces pendientes se marcan como usados y sus sesiones se revocan (si no, un enlace de activación viejo reactivaría la cuenta);
- correos y números se normalizan antes de calcular su hash;
- una lectura no es anterior al inicio de su asignación;
- a toda hora hay al menos un contacto activo y disponible para las alertas medias y graves (1.5.5).

### Borrado al fin del piloto (4.4.5)

- Las relaciones `[delete: cascade]` borran todo lo del paciente: ficha, condiciones, documentos (el backend borra además los archivos del disco), configuración, umbrales, vínculos, asignaciones, lotes, lecturas, alertas, atenciones, intentos, avisos y seguimientos.
- Los `CONTACTO` no se borran en cascada, porque pueden ser de otro paciente. Después, el backend borra los externos que quedaron sin pacientes; los internos son de las cuentas del equipo y se conservan.
- `USUARIO`, `SMARTWATCH`, los catálogos y los `CONTACTO` con pacientes no se pueden borrar mientras algo los use.
- `REGISTRO_AUDITORIA` no tiene datos personales y se conserva.

## Cómo se probó

**Las migraciones (1.1.6)** se prueban en Java con JUnit y Testcontainers, sobre un PostgreSQL 18 real en Docker: [`ModeloDatosTest`](../adapters/src/test/java/cl/ucn/alertacuida/adapters/persistencia/ModeloDatosTest.java) aplica las migraciones con Flyway y recorre los mismos casos que se describen abajo, cada uno como una prueba con su nombre. Corren con `./mvnw verify` y en el CI. Sin Docker se omiten (no fallan).

**El diseño (0.4)** se probó antes de las migraciones con las pruebas de [`pruebas-modelo-datos/`](pruebas-modelo-datos/), que crean la base desde el `.dbml` en un PostgreSQL embebido; el resultado está en el informe [**Pruebas del modelo de datos**](pruebas-modelo-datos.md). Para repetirlas: `cd docs/pruebas-modelo-datos && npm install && npm test`.

El script crea la base desde este `.dbml` en un PostgreSQL embebido (PGlite, con `btree_gist`), aplica `reglas-extra.sql` y recorre **285 casos en 20 grupos**: relaciones uno a uno, estructura, roles y permisos, inicio de sesión, pacientes, contactos, configuración y umbrales, relojes y asignaciones, datos del reloj, configuración pendiente, el flujo completo de una alerta, la atención, gravedad y horario, cancelación y simulacro, alertas técnicas, idempotencia, avisos a contactos, datos calculados, auditoría y borrado al fin del piloto.

En cada dato que debe rechazarse se verifica también el motivo (clave única, `CHECK`, clave foránea, `NOT NULL`, superposición o valor fuera del enum). Las reglas que valida el backend se prueban para dejar constancia de que la base las acepta a propósito.

El 20 de octubre de 2026: **285 casos: 272 correctos y 13 que valida el backend, sin fallas.**

## Cambios respecto de la versión 1 (19 tablas)

| Antes | Ahora | Por qué |
|---|---|---|
| `PACIENTE` con todo | `PACIENTE` (identidad) + `FICHA_PACIENTE` + `CONDICION_PACIENTE` (enfermedad o situación) | Separar identidad de salud (0.4.2); las condiciones son un grupo repetido (1FN) |
| Sin documentos | `DOCUMENTO` | Informe sociosanitario y consentimiento firmado, con hash contra alteraciones |
| Umbrales dentro de `CONFIGURACION` | `UMBRAL`, una fila por variable y con historial propio | Cambiar el GPS copiaba los umbrales, y agregar una variable obligaba a agregar columnas |
| Reglas de tendencia y módulos en `jsonb` | Una columna por módulo; la regla de tendencia, en el backend | 1FN; la lógica de la regla no es un dato del paciente |
| `version_configuracion` (fecha), `fecha_modificacion` y la vista `ULTIMO_CAMBIO_CONFIGURACION` | `hash_configuracion` | Una sola comparación cubre los cambios en la base y en el yml |
| Atención del equipo dentro de `ALERTA` | `ATENCION_ALERTA` (uno a uno) | Dos autores en una fila: la evidencia del reloj se tocaba cuando el equipo trabajaba |
| Un solo estado | Estado de contacto + estado de atención | Lo que hace el reloj y lo que hace el equipo avanzan por separado |
| Índice parcial de alerta del servidor abierta | Lo valida el job con un bloqueo consultivo | "Abierta" ahora depende de `ATENCION_ALERTA` |
| Sin folio | `ALERTA.numero` | Número legible para el equipo |
| Lecturas sin `id_asignacion` | `id_asignacion` con clave foránea compuesta | Evitar duplicar lecturas reenviadas en otro lote (ver [Normalización](#normalización)) |
| Seguimiento solo de las positivas, sin destino de derivación | `SEGUIMIENTO` sobre cualquier alerta abierta + `DESTINO_DERIVACION` | Catálogo de destinos; notas también antes del cierre |
| `USUARIO.rol` fijo | `ROL` + `PERMISO_ROL` | Permisos por módulo y acción, editables sin cambiar la tabla |
| Sin enlaces por correo | `TOKEN_ACCION` (activación y recuperación) | El administrador crea la cuenta y el usuario define su contraseña |
| Tipo de contacto `CUIDADOR` | `es_cuidador_principal` | Ser cuidador es un rol en el vínculo, no quién es la persona |
| Índices parciales de asignación vigente | `EXCLUDE` sin superposición | También impide superponer asignaciones ya cerradas |

**Revisión del 20 de octubre de 2026** (resultado de las pruebas):

| Antes | Ahora | Por qué |
|---|---|---|
| `Ref` uno a uno con `-` | Con `>` | `-` invertía la clave: borrar una ficha borraba al paciente, y ninguna alerta podía llegar sin atención |
| `pg_advisory_xact_lock(id_asignacion, tipo_evento)` | `pg_advisory_xact_lock(hashtextextended(...))` | La función solo recibe números |
| Asumir con `WHERE fecha_hora_toma IS NULL` | `... AND fecha_hora_cierre IS NULL` | Una alerta cerrada por el sistema chocaba con un `CHECK` en vez de no afectar filas |
| `UMBRAL` con columnas sueltas | Fila completa o vacía, en orden, SpO2 hasta 100 % | Una columna vacía podía significar "sin límite" o "valor base", y se aceptaban rangos incoherentes |
| `rut` opcional | Obligatorio | Sin él, el hash no se puede revertir y se pierde el RUN |
| Cualquier origen con cualquier tipo | El servidor solo crea técnicas y simulacros; el silencio de datos solo lo crea el servidor | `CHECK` |
| Un aviso por `event_id` | Además, uno por contacto, canal y ronda | Un reloj reiniciado podía mandar dos mensajes a la misma persona |
| Correo de usuario con mayúsculas | En minúsculas (`CHECK`) | El único dejaba registrar dos veces el mismo correo |
| Entidades de auditoría incompletas | Más `CONTACTO_PACIENTE`, `FICHA_PACIENTE`, `CONDICION_PACIENTE`, `PERMISO_ROL`, y formato de `id_entidad` para claves compuestas | Se registraban con un id que no era el suyo |
| Reglas entre tablas sin dueño | Anotadas como "lo valida el backend" | Consentimiento, reloj operativo, baja, cancelada, canal y opt-in, rol de sistema, edición de `ALERTA` |
| Otros `CHECK` menores | Tokens, `intentos >= 0`, descartes sin `SILENCIO_DATOS` | Datos imposibles |
| `CONDICION_PACIENTE` sin tipo | `tipo`: `ENFERMEDAD` o `SITUACION` | Separar enfermedades de situaciones (discapacidad, movilidad reducida) |
| Claves foráneas sin índice | Seis índices nuevos | Borrados en cascada y consultas frecuentes (ver [Migraciones](#migraciones-11)) |
| `fecha_nacimiento date` cifrada | `varchar` | Un dato cifrado no cabe en una columna `date` |
