# Backend: qué hace el código

Resumen del backend a la fecha (22-10-2026). Hoy hace cuatro cosas:
- crea la base de datos;
- permite iniciar sesión al personal;
- permite al administrador crear cuentas del equipo, que cada persona activa eligiendo su contraseña;
- administra los pacientes (en el dashboard, "usuarios"), los relojes y el vínculo entre ambos.

Cómo levantarlo: [README](../README.md). Cada clase tiene Javadoc con el detalle.

Referencia rápida de los endpoints disponibles, con ejemplos JSON: [para el dashboard](endpoints-dashboard.md) y [para el reloj](endpoints-reloj.md). En formato OpenAPI: [api-dashboard.yaml](api-dashboard.yaml) y [api-reloj.yaml](api-reloj.yaml). Las alertas, sus reglas y sus decisiones están reunidas en [alertas.md](alertas.md).

## Capas (arquitectura hexagonal)

Un módulo Maven por capa. Cada capa solo conoce a la de adentro.

| Módulo | Qué contiene | Depende de |
|---|---|---|
| `domain` | Reglas puras del negocio. Hoy: `Run` (valida el dígito verificador y normaliza el RUN a `12345678-5`), `ZonaHoraria`, `PoliticaContrasena` y los valores posibles de sexo, nivel de dependencia, tipo de condición y estado del reloj. | Nada |
| `application` | Casos de uso y los puertos (interfaces) que necesitan. Java puro, sin Spring. | `domain` |
| `adapters` | Implementa los puertos: base de datos (JDBC), seguridad (bcrypt, HMAC, JWT) y controladores REST. Incluye las migraciones de Flyway. | `application` |
| `app` | Arranque de Spring Boot y las clases `Configuracion…`, el único lugar que une los casos de uso con sus adaptadores. | Todos |

Así, las reglas del login se prueban sin base de datos ni Spring (`IniciarSesionTest`). Cambiar, por ejemplo, JDBC por otra cosa solo toca `adapters`.

## Carpetas

Dentro de cada capa, una carpeta por funcionalidad: `sesion`, `personal` (cuentas del equipo), `paciente`, `condicion` (enfermedades y situaciones del paciente), `clinica` (perfil clínico: módulos y umbrales), `contacto` (contactos de cada paciente), `reloj` (inventario), `asignacion` (vínculo reloj-paciente), `telemetria` (lotes que envía el reloj), `alerta`, `vigilancia` (reglas de ausencia), `aviso` (correo institucional), `calendario` (horario hábil, feriados y enrutamiento por gravedad) y `auditoria`. Los controladores y la seguridad de la API del reloj van en `adapters/web/dispositivo` y `adapters/seguridad/dispositivo`. Cada una tiene estas subcarpetas:

```
application/<funcionalidad>/        casos de uso (las clases que "hacen" algo)
application/<funcionalidad>/modelo/ datos que entran y salen de los casos de uso (records)
application/<funcionalidad>/error/  excepciones de esa funcionalidad
application/<funcionalidad>/puerto/ interfaces que implementa adapters
application/comun/                  lo que usan varias funcionalidades: puertos (Transacciones, HashRun,
                                    SecretosUnSoloUso) y errores (SolicitudRechazada 400, NoEncontrado 404,
                                    Conflicto 409)
domain/comun/                       Run y ZonaHoraria

adapters/persistencia/<funcionalidad>/  implementaciones JDBC de los puertos
adapters/seguridad/                     configuración de Spring Security y token JWT
adapters/seguridad/hash/                hashes y secretos (bcrypt, HMAC del RUN, tokens aleatorios)
adapters/seguridad/cifrado/             cifrado de los datos personales (CifradoCampos, ColumnaCifrada)
adapters/correo/                        canales de correo: SMTP (Spring Mail) y simulado (solo log)
adapters/web/<funcionalidad>/           controladores REST y sus cuerpos de entrada y salida
adapters/web/                           Permisos y ManejadorErrores, comunes a todos los controladores

app/configuracion/                  une casos de uso y adaptadores: ConfiguracionCasosDeUso (sesión y
                                    personal) y ConfiguracionPacientesYRelojes
app (pruebas)/<funcionalidad>/      pruebas de punta a punta; soporte/ tiene el PostgreSQL de prueba
```

Las pruebas unitarias van en la misma carpeta (paquete) que la clase que prueban.

## Base de datos

Al arrancar, Flyway aplica las migraciones de `adapters/src/main/resources/db/migration`:

| Migración | Contenido |
|---|---|
| V1 | Personal, roles, permisos y auditoría |
| V2 | Pacientes, datos de salud y contactos |
| V3 | Configuración clínica (umbrales, reglas, versiones) |
| V4 | Relojes y telemetría (sincronizaciones, lecturas, ubicaciones) |
| V5 | Alertas y su seguimiento |
| V6 | Catálogos iniciales: los 3 roles y sus permisos |
| V7 | Acción `TOKEN_REUTILIZADO` en la auditoría |
| V8 | Módulo de tendencia activo por defecto y para quienes lo tenían apagado |

Con el perfil `dev` se carga además `db/ejemplo/R__datos_de_ejemplo.sql`, con el administrador de prueba. El detalle de las tablas está en [modelo-datos.md](modelo-datos.md).

## Inicio de sesión

Endpoints:

- `POST /api/auth/login`: recibe `{run, contrasena}` y devuelve el token, su vencimiento, el plazo de inactividad y el usuario con sus permisos. Deja el token de renovación en una cookie.
- `POST /api/auth/renovar`: cambia la cookie por tokens nuevos, sin pedir la contraseña.
- `POST /api/auth/salir`: cierra la sesión de este navegador. Responde 204 aunque no haya sesión.
- `GET /api/auth/yo`: devuelve el usuario del token.

Recorrido de un login:

1. `SesionController` recibe la petición. Solo traduce HTTP; las reglas están en el caso de uso.
2. `IniciarSesion` normaliza el RUN y le calcula el hash (`HashRunHmac`). Con ese hash busca al usuario (`CuentaUsuarioJdbc`), sin tener que descifrar los RUN guardados.
3. Cuenta los intentos fallidos recientes en `registro_auditoria` (`RegistroAccesosJdbc`). La cuenta queda bloqueada con 5 fallos en 15 minutos sin un login correcto entre medio.
4. Compara la contraseña con su hash bcrypt (`VerificadorBcrypt`).
5. Si todo está bien, registra `LOGIN` y `AperturaSesion` entrega un token JWT (`TokenJwt`) con el id, el nombre, el rol y los permisos (`"ALERTAS:VER"`, …), más el token de renovación. Si algo falla, registra `LOGIN_FALLIDO` y responde 401.

Después, cada petición manda el token en la cabecera `Authorization: Bearer …`. Spring Security revisa su firma y su vencimiento, y convierte los permisos en *authorities*. El token de acceso no se guarda en el servidor; el de renovación sí, como hash en `token_sesion`.

Decisiones de seguridad:

- **Mismo mensaje para cualquier falla** ("RUN o contraseña incorrectos"), y el mismo tiempo de respuesta: si el RUN no existe, igual se ejecuta bcrypt con un hash de relleno. Así no se puede averiguar qué RUN están registrados.
- **El hash del RUN lleva clave (HMAC-SHA256)**: los RUN posibles son pocos, así que un SHA-256 simple se podría revertir probándolos todos.
- **El bloqueo se calcula desde la auditoría**: no hay un contador que mantener ni que se pueda desincronizar.
- **Sin CSRF**: el token de acceso no va en una cookie, así que el navegador no lo manda solo. La cookie de renovación es `SameSite=Strict`, solo viaja a `/api/auth` y no da acceso a ningún dato por sí misma.
- **Los permisos viajan en el token**: si cambian, se notan en la siguiente renovación, porque esta los vuelve a leer.
- **La contraseña no aparece en los logs** (`SolicitudLogin.toString`).

## Autorización por permiso

Hay dos niveles:
1. `ConfiguracionSeguridad` exige un token válido en todo, salvo `login` y `activar`. Sin token, la respuesta es **401**.
2. Cada endpoint exige su permiso con `@PreAuthorize(Permisos.X)`. Sin el permiso, la respuesta es **403**. Las reglas están juntas en `adapters/web/Permisos.java`, para no escribirlas a mano en cada controlador.

Importante: el módulo `USUARIOS` son los **pacientes**. Las cuentas del personal van en `ADMINISTRACION`, que solo tiene el administrador.

## Cuentas del personal

Endpoints:

| Endpoint | Permiso | Qué hace |
|---|---|---|
| `POST /api/personal` | `ADMINISTRACION:CREAR` | Crea la cuenta y devuelve el enlace de activación (201). |
| `POST /api/personal/{id}/enlace-activacion` | `ADMINISTRACION:CREAR` | Emite otro enlace si el anterior se perdió o venció; el anterior deja de servir. |
| `POST /api/auth/activar` | Público | Recibe `{token, contrasena}`, guarda la contraseña y activa la cuenta (204). |

Recorrido:

1. El administrador escribe el RUN, el nombre, el cargo, el correo, el rol y, si corresponde, el teléfono con WhatsApp y si la persona aceptó recibir avisos por ese medio. `CrearCuentaPersonal`:
   - valida el RUN y el rol;
   - exige teléfono al rol `COORDINADOR`, que recibe por WhatsApp los avisos de la oficina PRLAC; lo normaliza con `NumeroWhatsapp` y el adaptador lo cifra (`usuario.telefono`);
   - si la persona aceptó WhatsApp, guarda la fecha del opt-in (`usuario.fecha_opt_in`); sin teléfono, se rechaza;
   - rechaza un RUN o correo repetido (409);
   - guarda la cuenta **inactiva y sin contraseña**;
   - emite el enlace;
   - lo registra en la auditoría.

   Todo en una transacción.
2. El enlace es `http://…/activar#<token>` y vence en 48 h. Mientras no exista el correo, el administrador lo copia y se lo pasa a la persona.
3. La persona abre el enlace y elige su contraseña. `ActivarCuenta`:
   - revisa que el token exista, no esté usado ni vencido;
   - valida la contraseña con `PoliticaContrasena`;
   - guarda el hash bcrypt;
   - activa la cuenta y marca el enlace como usado.
4. Desde ahí, la persona inicia sesión como cualquiera.

Decisiones:

- **Nadie más conoce la contraseña**: ni el administrador la escribe ni viaja por otro medio.
- **El token es aleatorio (256 bits) y la base solo guarda su SHA-256**: quien lea la base no obtiene enlaces que funcionen. Un hash simple basta porque, a diferencia de una contraseña, el token no se puede adivinar.
- **El token va después de `#`**: esa parte de la URL no se envía al servidor, así que no queda en ningún log.
- **Política de contraseñas (NIST)**: al menos 12 caracteres y como máximo 72 bytes (el límite de bcrypt). No puede estar en la lista de comunes (`domain/.../contrasenas-comunes.txt`) ni ser el RUN de la persona. No se exigen mayúsculas ni símbolos.
- **"No puede ser el RUN" se revisa con el hash**: no hace falta leer (ni descifrar) el RUN guardado.
- **Un enlace sirve una sola vez, aunque se use en dos pestañas a la vez**: las actualizaciones tienen condiciones (`WHERE fecha_uso IS NULL`, `WHERE hash_contrasena IS NULL`).
- **Transacciones sin Spring en los casos de uso**: usan el puerto `Transacciones`, implementado con `TransactionTemplate` en `adapters`.

## Pacientes y relojes

En el dashboard, a los pacientes se les llama "usuarios" (término del PRLAC); en el código y la base, "pacientes". Todo usa los permisos del módulo `USUARIOS`, que tienen el coordinador y el administrador.

| Endpoint | Permiso | Qué hace |
|---|---|---|
| `GET /api/pacientes?incluirBajas=` | `VER` | Lista con edad y reloj actual. |
| `GET /api/pacientes/{id}` | `VER` | Ficha con el historial de relojes. |
| `POST /api/pacientes` | `CREAR` | Alta (identidad). |
| `PUT /api/pacientes/{id}` | `EDITAR` | Edición, incluido el RUN si se escribió mal. |
| `POST /api/pacientes/{id}/baja` | `ELIMINAR` | Baja con fecha de hoy; cierra su reloj. No borra nada. |
| `GET /api/pacientes/{id}/condiciones` | `VER` | Enfermedades y luego situaciones, por orden alfabético. |
| `POST /api/pacientes/{id}/condiciones` | `EDITAR` | Agrega una enfermedad o situación (texto libre); 409 si ya la tiene. |
| `DELETE /api/pacientes/{id}/condiciones/{idCondicion}` | `EDITAR` | Quita una condición; 404 si es de otro paciente. |
| `POST /api/pacientes/{id}/reloj` | `EDITAR` | Vincula un reloj; devuelve su token (201). |
| `POST /api/pacientes/{id}/reloj/reemplazo` | `EDITAR` | Cambia el reloj por otro; el anterior puede quedar "en reparación". |
| `DELETE /api/pacientes/{id}/reloj` | `EDITAR` | Retira el reloj; su token deja de servir. |
| `POST /api/pacientes/{id}/reloj/token` | `EDITAR` | Otro token para el mismo reloj. |
| `GET /api/relojes` | `VER` | Inventario con el paciente que usa cada reloj. |
| `POST /api/relojes` / `PUT /api/relojes/{id}` | `CREAR` / `EDITAR` | Registro y edición (modelo, SIM, plan, estado). |

Reglas, en `VinculoReloj`, `EditarPaciente`, `EditarReloj` y `CondicionesPaciente`:

- Solo se vinculan relojes `OPERATIVO` a pacientes **con consentimiento** y **sin baja**.
- Un reloj con un solo paciente a la vez, y un paciente con un solo reloj. Si dos personas vinculan a la vez, la base lo impide igual (restricciones `EXCLUDE` de V4).
- El reemplazo cierra el vínculo anterior y abre el nuevo **en el mismo instante**, así el paciente no queda sin reloj.
- No se puede quitar el consentimiento a quien tiene reloj, ni sacar de servicio un reloj en uso: primero hay que desvincularlo.
- La baja cierra el vínculo en la misma transacción.
- Una condición no se repite en el mismo paciente con el mismo tipo, aunque cambien las mayúsculas. Como la edición, se permite también con pacientes dados de baja.

Decisiones:

- **El historial nunca se pierde**: los vínculos se cierran con fecha de fin, no se borran. Así se sabe qué reloj mandó cada dato.
- **Token del reloj (rol DEVICE)**: 256 bits aleatorios, se muestran una sola vez y en la base va su SHA-256, igual que el enlace de activación. Cerrar el vínculo lo revoca. El reloj lo usará cuando exista su API.
- **La edad no se guarda**: se calcula con la fecha de hoy en Chile (`ZonaHoraria`).
- **Datos personales cifrados** (ver [Cifrado](#cifrado-de-datos-personales)): RUN, nombres, apellidos, fecha de nacimiento, dirección y la descripción de las condiciones. Por eso la lista se ordena en Java y la búsqueda se hace en el dashboard: la base no puede ordenar ni buscar datos cifrados. Por lo mismo, las condiciones repetidas se detectan en el caso de uso y no con un `UNIQUE`.
- **Enfermedades y situaciones en texto libre**: el dashboard sugiere las comunes, pero se puede escribir cualquiera.
- La ficha social (registro de discapacidad, notas) y los documentos quedan para más adelante.

## Perfil clínico y contactos

Lo que el reloj necesitará en su configuración (1.7): qué vigila, con qué umbrales y a quién avisa. Se edita desde la ficha del usuario (secciones "Contactos" y "Perfil clínico"). Ver exige `USUARIOS:VER`; cambiar, `USUARIOS:EDITAR`.

| Endpoint | Permiso | Qué hace |
|---|---|---|
| `GET /api/pacientes/{id}/perfil-clinico` | `VER` | Módulos, opciones y umbrales vigentes, con los valores base donde no hay ajustes. |
| `PUT /api/pacientes/{id}/perfil-clinico/ajustes` | `EDITAR` | Módulos (incluidos zona segura y botón de ayuda), GPS, modo conservador, minutos de espera y motivo. |
| `PUT /api/pacientes/{id}/perfil-clinico/zona-segura` | `EDITAR` | Define o cambia la zona segura: centro, radio y gravedad. No la activa. |
| `PUT /api/pacientes/{id}/perfil-clinico/umbrales/{FC\|SPO2}` | `EDITAR` | Ajusta el umbral de una variable. |
| `DELETE /api/pacientes/{id}/perfil-clinico/umbrales/{FC\|SPO2}` | `EDITAR` | Vuelve al umbral base (agrega una fila vacía). |
| `GET /api/tipos-contacto` | `VER` | Catálogo de tipos (familia, vecino…). |
| `GET /api/pacientes/{id}/contactos` | `VER` | Activos por prioridad, luego desactivados, y las horas sin cobertura. |
| `POST /api/pacientes/{id}/contactos` | `EDITAR` | Agrega un contacto al final de la lista. |
| `PUT /api/pacientes/{id}/contactos/{idContacto}` | `EDITAR` | Reemplaza sus datos. |
| `PUT /api/pacientes/{id}/contactos/{idContacto}/opt-in` | `EDITAR` | Registra que aceptó (o revocó) recibir WhatsApp. |
| `POST /api/pacientes/{id}/contactos/{idContacto}/desactivar` y `/reactivar` | `EDITAR` | Lo saca de la lista sin borrarlo, o lo devuelve al final. |
| `PUT /api/pacientes/{id}/contactos/orden` | `EDITAR` | Nuevo orden de aviso; debe traer a todos los activos. |

Perfil clínico (`PerfilClinicoPaciente`):

- **Historial sin modificaciones**: cada cambio agrega una fila a `configuracion` o a `umbral`, con auditoría (`CREAR CONFIGURACION` o `UMBRAL`, con la clave compuesta como id). Rige la de mayor `vigente_desde`. Si no cambia nada (salvo el motivo), no se agrega fila: así no se crea una versión de configuración nueva para el reloj.
- **Valores base** en `application.properties` (`alertacuida.clinica.*`, ver [Configuración](#configuración)), validados al arrancar con las mismas reglas que los de un paciente. Son **provisionales** hasta que los valide el equipo clínico (Anexo B).
- **Umbrales** (`Umbral`, en el dominio): grave bajo < mínimo normal < máximo normal < grave alto, dentro de lo que mide el sensor. La SpO2 no tiene límites altos. Volver al base agrega una fila vacía, sin borrar el historial.
- Sin configuración rigen los valores por defecto de V3, V8 y V9 (todos los módulos activos menos la zona segura; GPS y modo conservador apagados). Los minutos de espera van de 1 a 60; vacíos = el valor base.
- **Zona segura** (`ZonaSegura`, en el dominio; tabla `zona_segura`, migración V9): un círculo con centro (latitud y longitud, guardadas cifradas porque suelen ser el domicilio), radio de 50 a 5000 m y la gravedad de la alerta al salir (media si no se elige). Tiene historial propio, como el umbral, con auditoría `CREAR ZONA_SEGURA`. Guardarla no la activa: el módulo `zonaSegura` se activa en los ajustes y exige el GPS (regla en la base) y una zona guardada (lo revisa `PerfilClinicoPaciente`). La revisa el reloj con su GPS; cada cuánto lo decide la app del reloj.
- **Botón de ayuda** (módulo `botonAyuda`, activo por defecto desde V9): el reloj envía una alerta `BOTON_AYUDA`, siempre leve. Avisa primero a los contactos `VECINO` y después al resto (ver `ReglaEnrutamiento`).

Contactos (`ContactosPaciente`):

- **Una persona, una vez**: si el WhatsApp o el correo ya son de alguien registrado (`whatsapp_hash`, `correo_hash`), se vincula esa persona, se actualiza su nombre y se completa el medio que le faltaba. Un WhatsApp de una persona con el correo de otra responde 409. Cambiar nombre o medios de contacto los cambia en todos sus usuarios (el dashboard lo advierte).
- **Normalización** antes del hash: `NumeroWhatsapp` (formato +56912345678; acepta 9 dígitos, con 56 o con +) y `Correo` (minúsculas, sin espacios). Los hash usan la clave de `rut_hash`, con un prefijo por medio (`HashMedioContactoHmac`).
- **El SAMU no puede ser contacto**: se rechazan el nombre "SAMU" y los números de emergencia (130 a 134). El mensaje de una alerta grave ya pide llamar al 131.
- **Opt-in de WhatsApp**: lo marca el equipo según el consentimiento de la persona (con la tarea 2.9 se sumará lo que informe Meta). Es de la persona y no del vínculo, porque Meta lo registra por número: vale para todos sus usuarios y **se pierde si cambia el número**. Sin opt-in vigente no se le envía WhatsApp.
- **Prioridad**: el contacto nuevo va al final; desactivar sube a los siguientes y reactivar lo deja al final, así nunca hay huecos. El índice único de prioridad no es diferible, por eso `ContactosJdbc.reordenar` primero aparta las prioridades y luego las asigna.
- **Cuidador principal**: uno por usuario. Marcar a otro se lo quita al anterior; un contacto desactivado no puede serlo.
- **Horario**: en hora de Chile; si el inicio es posterior al término, cruza la medianoche; vacío = todo el día. Se pide inicio y término, distintos.
- **Horas sin cobertura (aviso, no bloqueo)**: `Cobertura.sinCubrir` calcula los tramos del día sin ningún contacto **disponible** (activo, con correo o con WhatsApp aceptado). Las alertas medias y graves avisan a toda hora, así que la ficha los muestra en rojo. Se puede guardar igual, porque los contactos se cargan de a uno; exigirlo queda para cuando se entregue el reloj o para la configuración (1.7).
- **Desactivar, no borrar**: los avisos del contacto quedan en el historial (`registro_intento`).

## Calendario hábil y enrutamiento por gravedad

Deciden si una alerta leve avisa o espera al día hábil, y a quién se avisa.

| Endpoint | Permiso | Qué hace |
|---|---|---|
| `GET /api/calendario` | Solo sesión | Horario de cada día hábil y feriados, para la sección "Calendario" del dashboard (solo lectura). |

- **Dónde está**: en `app/src/main/resources/calendario.yml`, que importa `application.properties`. Es YAML porque Spring lee los `.properties` en Latin-1 y los nombres de los feriados llevan tildes. Cambiarlo requiere reiniciar el backend; si está mal escrito, no arranca y el error dice qué propiedad revisar (`ConfiguracionCalendario`).
- **Horario**: lunes a jueves de 08:30 a 17:30 y viernes de 08:30 a 16:30, en hora de Chile. El inicio cuenta y el fin ya no: a las 17:30 en punto es fuera de horario. Los días que no aparecen (sábado y domingo) no son hábiles.
- **Feriados**: los nacionales de 2026 y 2027 (sin los regionales), según feriados.cl, que cita la BCN. Los de 2027 son provisionales hasta que se publique el calendario oficial. Hay que agregarlos cada año: el reloj recibe el año en curso y el siguiente.
- **`CalendarioHabil`** (dominio): un momento es hábil si su día tiene horario, no es feriado y la hora cae dentro. Todo se calcula en `America/Santiago`, así que los cambios de hora de abril y septiembre no corren el horario.
- **`ReglaEnrutamiento`** (aplicación), fija en el código: una alerta leve fuera de horario o en feriado espera al día hábil; en cualquier otro caso se avisa a los contactos activos, con algún canal y cuyo horario cubre ese momento, por prioridad. Con el botón de ayuda (`BOTON_AYUDA`, siempre leve) van primero los `VECINO` por prioridad y después el resto por prioridad. El horario de cada contacto se respeta siempre, también en horario hábil.
- **Quién la usa**: el reloj, porque escala (lo recibe en su configuración); el servidor, para revisar el `esperaDiaHabil` de las alertas que recibe (2.1.2) y para sus alertas técnicas (2.2).

## Atención de alertas en el dashboard

Paquete `application/alerta`. `ConsultarAlertas` lee la lista y el detalle; `AtenderAlerta` asume, resuelve y cierra el seguimiento. Sin migración: usa `atencion_alerta`, `aviso_fcm` y la vista `alerta_estado`.

- **Línea de tiempo (`LineaTiempoAlerta`):** no se guarda. Se arma con las fechas de `alerta`, `registro_intento`, `atencion_alerta`, `seguimiento` y `aviso_fcm`, y se ordena por fecha. Hay `SALTO_CONTACTO` cuando un aviso a la lista del paciente va a otra persona que el aviso anterior a esa lista; el respaldo al mismo contacto y los avisos al equipo no cuentan.
- **Sin carreras:** asumir y resolver son un `INSERT … ON CONFLICT … WHERE` en `AtencionAlertasJdbc`. De dos personas a la vez, la segunda no afecta filas y recibe 409.
- **Resolver:** si nadie la asumió, se guarda a quien resuelve como responsable, con la hora de toma igual a la del cierre. Por eso la línea de tiempo omite `CASO_ASUMIDO` cuando ambas horas coinciden.
- **Aviso al reloj:** al resolver una alerta con origen `RELOJ`, la misma transacción deja en `aviso_fcm` una fila `ALERTA_RESUELTA` sin fecha de envío, que toma `EnviarAvisosReloj`.
- **Cifrado:** `observaciones_cierre` y `seguimiento.nota` van cifradas (`ColumnaCifrada`).
- **Auditoría:** las lecturas quedan como `LEER ALERTA` y los cambios como `MODIFICAR ATENCION_ALERTA`, sin las observaciones.

## Avisos al reloj por Firebase

Paquete `application/avisoreloj`. Sin migración: usa `aviso_fcm` y `smartwatch.token_fcm`.

- **Canal:** `alertacuida.fcm.canal` elige `SIMULADO` (por defecto: solo registra en el log), `SIMULADOR` o `FIREBASE`.
  - `FIREBASE`: `FCM_CREDENCIALES` con la ruta de la llave de la cuenta de servicio, fuera del repositorio (`.gitignore` bloquea `firebase*.json`). Sin llave legible, el backend no arranca. `CanalAvisoRelojFirebaseTest` prueba la llave contra Firebase real: solo corre si existe `FCM_CREDENCIALES`, así que en las pruebas normales y en GitHub se omite.
  - `SIMULADOR` (`CanalAvisoRelojSimulador`): entrega el aviso por HTTP al simulador de reloj (`simulador-reloj/`) en `FCM_SIMULADOR_URL` (por defecto `http://localhost:9090/avisos`), con el token y los mismos datos que irían por Firebase. 404 o 410 cuentan como token rechazado; otro error o el simulador apagado, como falla. Con Docker, el backend llega al simulador por `host.docker.internal`.
- **Bandeja (`EnviarAvisosReloj`):** corre cada `alertacuida.fcm.intervalo` (15 s) sobre los avisos sin acuse de relojes con token. `fecha_hora_envio` guarda el último envío e `intentos` cuenta los envíos.
- **Reintentos:** `alertacuida.fcm.reintentos` (`PT1M,PT1M,PT2M`), contados desde el envío anterior; a la hora justa ya toca. Son 4 envíos como máximo.
- **Fallas:** un envío que Firebase no acepta no cuenta como intento y se repite en la siguiente pasada. Si rechaza el token (`UNREGISTERED` o `INVALID_ARGUMENT`), se borra y el aviso espera uno nuevo; la condición sobre el token evita borrar uno registrado mientras tanto.
- **Sin confirmar:** agotados los reintentos, pasada una espera igual a la última sin acuse, la línea de tiempo muestra `FCM_NO_CONFIRMADO`. Se calcula, no se guarda.
- **Configuración nueva (`AvisarConfiguracionNueva`):** `ContactosPaciente` la llama después de guardar cada cambio de la lista. Es un intento único y no queda registrado. Si la persona es contacto de varios pacientes, solo se avisa al reloj del paciente que se editó; los demás se enteran en su sincronización.

## API del reloj

Lo que llama el reloj (o el simulador), según el contrato de [api-reloj.md](api-reloj.md). Implementado: sincronización, alertas (con discrepancias y exceso), cancelaciones, agravamiento, fin de la lista, estado de una alerta, configuración, token de Firebase y acuse de avisos FCM. Falta: solicitud de aviso (2.10) y webhook de WhatsApp (2.9).

| Endpoint | Caso de uso | Guarda en |
|---|---|---|
| `POST /api/reloj/v1/sincronizaciones` | `RecibirSincronizacion` | `sincronizacion`, `registro_vital`, `ubicacion`, `evento_descartado` |
| `POST /api/reloj/v1/alertas` | `RecibirAlerta.recibir` | `alerta` (origen `RELOJ`, evidencia en `jsonb`), `discrepancia_alerta` y, si hay exceso, una `alerta` técnica |
| `POST /api/reloj/v1/cancelaciones` | `RecibirAlerta.registrarCancelacion` | `alerta` con `fecha_hora_cancelacion` |
| `POST /api/reloj/v1/alertas/{eventId}/agravamiento` | `RecibirAlerta.agravar` | `alerta.fecha_hora_agravamiento` y `segundos_inmovilidad_agravamiento` |
| `POST /api/reloj/v1/alertas/{eventId}/sin-respuesta` | `RecibirAlerta.informarSinRespuesta` | `alerta.fecha_hora_sin_respuesta` (repetirlo no cambia la hora) |
| `GET /api/reloj/v1/alertas/{eventId}` | `RecibirAlerta.estado` | — (lee la vista `alerta_estado`) |
| `GET /api/reloj/v1/configuracion` | `ConfiguracionDelReloj.armar` | — (se calcula de lo vigente) |
| `PUT /api/reloj/v1/token-fcm` | `RegistrarTokenFcm` | `smartwatch.token_fcm` y `fecha_token_fcm` |
| `POST /api/reloj/v1/avisos-fcm/{idAviso}/recepcion` | `AcusarAvisoReloj` | `aviso_fcm.fecha_hora_recepcion` (repetirlo no cambia la hora) |

**Autenticación (cadena de seguridad aparte).** `/api/reloj/**` tiene su propia `SecurityFilterChain`, que va antes que la del dashboard. Solo acepta el token del reloj: Spring lo trata como token "opaco" y `IntrospectorTokenReloj` lo busca por su SHA-256 entre los vínculos **vigentes** (`AutenticarReloj`). Un token de un vínculo cerrado, desconocido o ausente responde 401 `TOKEN_INVALIDO`. El token del reloj no abre el dashboard (no es un JWT) y el del dashboard no abre la API del reloj. El controlador recibe el vínculo (`PrincipalReloj`), nunca un id de paciente enviado por el reloj.

Reglas:

- **Idempotencia:** `INSERT … ON CONFLICT DO NOTHING` por `event_id`. Un reenvío devuelve lo guardado la primera vez (`loteRepetido: true`; la alerta, con 200 en vez de 201). Dos reenvíos simultáneos tampoco duplican. Un `eventId` de otro reloj responde 409.
- **Lecturas:** se descartan una por una (sin rechazar el lote) si están fuera del rango del sensor (FC de 20 a 250, SpO2 de 50 a 100, en `TipoLectura`), si son futuras (más de 5 min) o anteriores a la entrega del reloj. Las repetidas (misma asignación, tipo y hora) las ignora la base. Se insertan en un solo viaje (batch).
- **Alertas:** nunca se rechazan por su contenido, solo si están mal formadas. Una media o grave marcada "espera día hábil" se guarda sin la marca (esas avisan siempre). Un `BOTON_AYUDA` que llega con otra gravedad se guarda como leve (regla `alerta_boton_leve` de V9), para no perder un pedido de ayuda. `SILENCIO_DATOS` no lo envía el reloj.
- **Discrepancias (`RevisionDiscrepancias`, 2.1.2):** al recibir una alerta nueva (no las cancelaciones) se compara con lo vigente del paciente y se guarda en `discrepancia_alerta` lo que no calza. Revisa cuatro cosas: `VERSION_DISTINTA` (la versión que informa no es la que calcula hoy `ConfiguracionDelReloj`), `MODULO_APAGADO` (el módulo de ese tipo está apagado; `FC_SPO2` exige FC y SpO2), `GRAVEDAD_DISTINTA` (tendencia distinta de `severidad-tendencia`, zona distinta de la de su zona; FC y SpO2 esperan la matriz del Anexo B) y `LECTURAS_EN_RANGO` (FC o SpO2: ninguna lectura más allá del límite grave; tendencia y `FC_SPO2`: todas dentro del rango normal; sin lecturas no se marca). Si la revisión misma falla, la alerta se guarda igual, sin discrepancias.
- **Exceso de alertas (2.1.4):** en la misma transacción se cuentan las alertas del reloj del vínculo en la ventana: no cuentan las que el paciente canceló, y una recibida hace justo 60 minutos ya no entra (decidido por ella el 05-11-2026). Desde la alerta número `avisar-desde` (10), contando la que llega, se crea una alerta técnica `EXCESO_ALERTAS` leve, salvo que ya haya una abierta (sin cierre del equipo) del mismo vínculo; `pg_advisory_xact_lock` evita crear dos si llegan a la vez. Nunca se responde 429 por esto.
- **Reglas de ausencia (`VigilarRelojes`, 2.2):** cada `intervalo` (5 min) la tarea programada aplica tres reglas a cada vínculo vigente, cada reloj en su transacción; si uno falla, se registra el error y sigue con los demás. Cada regla (`ReglaAusencia`) responde `ACTIVA` (crea la técnica, salvo que haya una abierta), `NORMAL` (la cierra, sin responsable) o `SIN_CAMBIO` (no alcanza para decidir). `ReglaSilencioDatos`: `silencio` (4 h) desde la última sincronización o, si nunca sincronizó, desde el vínculo; media. `ReglaBateriaBaja`: las 2 últimas sincronizaciones bajo `bateria-minima` (20 %); leve. `ReglaRelojNoPuesto`: las 2 últimas informan que no está puesto, ambas del mismo día y entre `hora-inicio-dia` y `hora-fin-dia` (hora de Chile); leve. Un lote que no informó el dato da `SIN_CAMBIO`. Los simulacros no bloquean una alerta real ni se cierran con ella. Si la última alerta de la regla la cerró una persona (`id_usuario_resolutor` con valor), no se crea otra hasta que una sincronización posterior demuestre que el reloj se normalizó (`ReglaAusencia.normaliza`); se calcula desde `sincronizacion`, sin columna nueva. Las técnicas leves creadas fuera del horario hábil, incluida `EXCESO_ALERTAS`, se guardan con `espera_dia_habil` (`ReglaEnrutamiento.esperaDiaHabil`) y quedan como `PENDIENTE_DIA_HABIL`. El cierre automático solo toca alertas con origen `SERVIDOR`: si el reloj llegara a enviar una `BATERIA_BAJA` o `RELOJ_NO_PUESTO` (pregunta 2 de [api-reloj.md](api-reloj.md)), la cierra el equipo y, mientras siga abierta, el servidor no crea otra. Los 2 lotes son la constante `VigilarRelojes.LOTES_SEGUIDOS`. Si el servidor estuvo caído más de 4 horas, la primera revisión puede crear silencios que se cierran solos cuando los relojes vuelven a sincronizar.
- **Agravamiento (2.1.3):** solo una caída leve o media, no cancelada (si no, 409 `NO_APLICA`); de otro reloj o desconocida, 404. La actualización tiene la condición `fecha_hora_agravamiento IS NULL`: repetirlo no cambia la hora.
- **Errores:** `ErroresApiReloj` responde en RFC 9457 con `codigo` (`MENSAJE_INVALIDO`, `ALERTA_DESCONOCIDA`, `NO_APLICA`, `LOTE_GRANDE`), solo para los controladores del reloj.
- **Hora del reloj:** si difiere más de 120 s de la del servidor, se acepta igual y la respuesta informa `desfaseRelojSegundos`.
- **Sin auditoría:** lo que escribe el reloj no va a `registro_auditoria`; queda en sus propias tablas con la hora de recepción.

### Configuración del reloj

Todo lo que el reloj necesita para evaluar y escalar sin conexión: módulos, GPS, modo conservador, umbrales, zona segura, `versionMatriz`, minutos de la regla de tendencia, minutos de espera, 2 rondas, contactos y calendario hábil.

- **Zona segura:** viaja solo con el módulo activo (si no, `null`). Así el reloj no recibe la ubicación del domicilio si no la usa, y cambiar una zona apagada no cambia la versión.

- **No se guarda: se calcula.** `ConfiguracionDelReloj` (paquete `application/configuracion`) la arma cada vez con `PerfilClinicoPaciente.consultar` (con los valores base donde el paciente no tiene ajustes), `ContactosPaciente.listar` y el `CalendarioHabil` de `calendario.yml`. Así no hay que "publicar" una configuración: cualquier cambio en la base, en `application.properties` o en `calendario.yml` cambia la versión sola.
- **Versión:** `ContenidoConfiguracion.huella()` = primeros 16 caracteres hexadecimales del SHA-256 de un texto canónico armado campo por campo (no de `toString()`, cuyo formato puede cambiar entre versiones de Java). Es la que el reloj informa en `estado.versionConfiguracion` y la que guarda `sincronizacion.hash_configuracion`.
- **Solo si cambió:** la versión viaja como ETag. Con `If-None-Match` igual a la vigente, `WebRequest.checkNotModified` responde 304 sin cuerpo. La sincronización responde `configuracionNueva: true` si la versión que informa el reloj no es la vigente (o no informa ninguna), también en un lote repetido. `RecibirSincronizacion` la pide a través de la interfaz `VersionVigente`, sin conocer cómo se arma.
- **Contactos:** solo los activos con algún canal usable (WhatsApp con opt-in vigente o correo), por prioridad, con id del vínculo, nombre, tipo (para que el botón de ayuda avise primero a los `VECINO`), horario y canales. Sin número ni correo. Puede haber saltos en la prioridad si un contacto no tiene canal.
- **Feriados:** los del año en curso y el siguiente, por año calendario en hora de Chile. Así la versión cambia una vez al año (el 1 de enero) y no cada vez que pasa un feriado.
- **`versionMatriz`:** `alertacuida.clinica.version-matriz` en `application.properties`, `PROVISIONAL` hasta recibir el Anexo B.
- **Regla de tendencia:** `alertacuida.clinica.minutos-tendencia` y `severidad-tendencia` viajan en `reglas.tendencia`. El servidor solo entrega los parámetros; quien evalúa es el reloj. Alerta cuando FC o SpO2 lleva ese tiempo fuera del rango normal, sin llegar al límite grave; si una lectura vuelve al rango normal, el tiempo parte de cero. El interruptor sigue siendo `modulos.tendencia`, por paciente. Ver [dónde cambiar cada parte](#regla-de-tendencia-dónde-cambiar-cada-parte).
- **Token de Firebase:** `RegistrarTokenFcm` lo guarda en el reloj físico (`smartwatch`), no en el vínculo; el nuevo reemplaza al anterior.
- **Qué versión aplica cada reloj (dashboard):** `ConsultarConfiguracionAplicada` compara la versión del último lote del reloj **vigente** con la que rige hoy: `AL_DIA`, `PENDIENTE`, `SIN_SINCRONIZAR` o `SIN_RELOJ`. "Aplicada desde" es el primer lote de la racha actual con esa versión (`SincronizacionesJdbc.versionInformada`). `GET /api/pacientes/{id}/reloj/configuracion` (`USUARIOS:VER`) lo entrega y la ficha lo muestra en la sección del reloj.

## Correo institucional

Avisos por correo desde la cuenta institucional, sin datos clínicos. Una tarea programada (`TareasProgramadas.avisarPorCorreo`, cada `alertacuida.correo.intervalo`) hace dos pasos; ni `RecibirAlerta` ni el vigilante saben del correo, así que guardar una alerta nunca espera ni falla por él.

1. **Encolar (`EncolarCorreos`).** Deja en la bandeja de salida (`registro_intento`, canal `CORREO`, con `fecha_hora_proximo_envio`):
   - el correo a cada cuenta activa con `recibe_avisos`, de cualquier rol (los coordinadores también, además de su WhatsApp), por las alertas técnicas (`TipoEvento.tecnicas()`), solo si la alerta ocurrió en horario hábil (`CalendarioHabil.esHabil`; fuera de él no se envía nada, ni al empezar la jornada). Las de salud no: a quién se avisa lo decide el reloj. La cuenta recibe el aviso como contacto interno (`contacto.id_usuario`), que se crea la primera vez. No se avisa de simulacros ni de alertas resueltas;
   - el respaldo de cada WhatsApp fallido (`fecha_hora_fallo`), al mismo destino y con la misma ronda, si tiene correo, a cualquier hora. El canal de WhatsApp (2.9) solo tiene que registrar la falla.

   Solo mira lo ocurrido dentro de `ventana` (1 h), para no avisar de alertas viejas. Encolar dos veces no duplica: lo impiden las claves únicas de `registro_intento`.
2. **Enviar (`EnviarCorreosPendientes`).** Toma hasta 100 correos cuyo próximo envío ya llegó, los redacta con `PlantillasCorreo` y los envía por el `CanalNotificacion` configurado. Si sale, guarda la hora y el `Message-ID` (`id_externo`). Si falla, lo reprograma a los 1, 5 y 15 minutos (`reintentos`); agotados, queda con `fecha_hora_fallo` y `motivo_fallo`. Un correo que falla no detiene a los demás.

**Plantillas (`PlantillasCorreo`).** Los textos están en `app/src/main/resources/correos.yml` (`TextosCorreo`), con marcadores como `{folio}` o `{alias}`; los comentarios del archivo dicen cuáles admite cada texto. Al arrancar se revisan: un marcador que no existe en ese texto, un texto vacío o una gravedad o técnica sin nombre impiden arrancar. `TextosCorreoRealesTest` comprueba que los textos reales sigan cumpliendo las reglas de abajo. Las graves salen con `Importance: High` y `X-Priority: 1`. Del paciente solo va el alias o, si no tiene, el folio de la alerta (`#142`). De una alerta clínica, solo su gravedad; de una técnica, qué le pasa al reloj (batería baja, reloj no puesto, exceso de alertas). Al equipo: silencio de datos, o alerta, con el enlace al dashboard (`url-dashboard`). Al contacto de un paciente: que se comunique con la persona y, si es grave, que llame al SAMU (131); sin el enlace al dashboard, pero con el enlace de respuesta (`{responder}`, obligatorio en su cuerpo; ver abajo).

**Respuesta del contacto por correo.** El enlace es `<url-dashboard>/responder#<token>`; la persona elige en la página. El token (`FirmaRespuesta`, implementado en `FirmaRespuestaHmac`) es `<id del intento>.<HMAC-SHA256>`, ambos en base64 para direcciones, firmado con `clave-respuesta`: no se guarda en la base y va después de `#` para que no llegue a los registros del servidor. La página pública `/responder` del dashboard llama a dos rutas públicas (`RespuestasContactoController`), las dos `POST` y con el token en el cuerpo:
- `POST /api/respuestas-contacto/consulta`: la alerta y lo que ya respondió, sin registrar nada.
- `POST /api/respuestas-contacto`: registra `VOY_EN_CAMINO` o `NO_PUEDO` en `registro_intento.respuesta` (`ResponderAviso`, `RespuestasContactoJdbc`).

Solo sirven los correos a un contacto de la lista del paciente; un token alterado o de una cuenta del equipo responde 400 con el mismo mensaje. Vale la primera respuesta (`UPDATE … WHERE respuesta IS NULL`). Con la alerta resuelta o cancelada no se registra. Un "voy" deja `ALERTA_CONFIRMADA` en `aviso_fcm`, y `alerta_estado` ya cuenta la alerta como confirmada. Decisiones en [alertas.md](alertas.md#correo-institucional).

**Canales (`alertacuida.correo.canal`).** `SMTP` envía con Spring Mail (`CanalCorreoSmtp`); el servidor, el usuario y la clave llegan por `SMTP_HOST`, `SMTP_PUERTO`, `SMTP_USUARIO` y `SMTP_CLAVE`, y `SMTP_AUTH` y `SMTP_TLS` los activan. `SIMULADO`, el valor por defecto, no envía nada: escribe en el log el asunto y el destinatario enmascarado (`CanalCorreoSimulado`). Con `docker compose`, el backend usa `SMTP` contra Mailpit, que guarda los correos sin enviarlos y los muestra en http://localhost:8025. El chequeo de salud del correo está apagado (`management.health.mail.enabled=false`): un servidor de correo caído no debe marcar el backend como caído.

## Cifrado de datos personales

Los adaptadores JDBC cifran al guardar y descifran al leer con `CifradoCampos`; los casos de uso no se enteran. Las columnas cifradas que ya usa el código están en `ColumnaCifrada`:

| Tabla | Columnas |
|---|---|
| `usuario` | `rut`, `telefono` |
| `paciente` | `rut`, `nombres`, `apellidos`, `fecha_nacimiento`, `direccion` |
| `condicion_paciente` | `descripcion` |
| `contacto` | `nombre`, `whatsapp`, `correo` |
| `configuracion` | `motivo_cambio` |
| `zona_segura` | `latitud`, `longitud` (como texto) |

Las demás columnas marcadas "cifrado" en las migraciones (`usuario.totp_secreto`, notas, observaciones) se agregan a `ColumnaCifrada` cuando se programe su adaptador.

Cómo funciona:

- **AES-256-GCM**: oculta el dato y detecta si alguien lo alteró (la etiqueta no calza y la lectura falla).
- **Vector inicial aleatorio por valor**: dos apellidos iguales quedan distintos en la base.
- **Formato**: `v1:` + base64(vector inicial + dato cifrado + etiqueta), en las mismas columnas `varchar`. El prefijo dice qué llave se usó, para cambiarla más adelante sin perder los datos antiguos.
- **Atado a su columna**: `"tabla.columna"` va como dato asociado. Un valor copiado a otra columna (por ejemplo, el RUN en la de nombres) no se descifra.
- **Búsquedas sin descifrar**: el RUN se busca por `rut_hash`, y los contactos por `whatsapp_hash` y `correo_hash` (HMAC con otra clave, `RUN_CLAVE`). La llave de cifrado y la del HMAC son distintas: filtrar una no revela la otra.
- **Un valor sin cifrar o dañado no se lee**: el backend falla en vez de mostrar datos posiblemente adulterados.

### La llave (`CIFRADO_CLAVE`)

- 32 bytes aleatorios en base64. Se generan con `openssl rand -base64 32`.
- Llega por variable de entorno, como `RUN_CLAVE` y `JWT_CLAVE`; nunca va en el repositorio ni en la base. Si falta o no tiene 32 bytes, el backend no arranca.
- Los perfiles `dev` y de pruebas traen llaves propias, fijas y públicas: **no usarlas en el servidor**.
- **Si se pierde la llave, los datos cifrados no se pueden recuperar.** En el servidor municipal hay que:
  1. generarla en el mismo servidor;
  2. dejarla en un archivo de entorno legible solo por el usuario del servicio (por ejemplo, `chmod 600`, dueño `alertacuida`), fuera de la carpeta de la aplicación y de los respaldos de la base;
  3. guardar una copia fuera del servidor (por ejemplo, en sobre sellado o en el gestor de secretos de TI), separada de los respaldos de la base: con ambos juntos, el respaldo deja de estar protegido.
- Por definir con TI: dónde queda la copia y quién tiene acceso.
- Cambiar la llave (pendiente): se agregaría `v2:` con la llave nueva, se leerían ambas y se volverían a cifrar los datos antiguos.

## Configuración

En `app/src/main/resources/application.properties`:

| Propiedad | Valor | Nota |
|---|---|---|
| `clave-run` | variable `RUN_CLAVE` | Si cambia, hay que recalcular el `rut_hash` de todos los usuarios y los hash de los contactos |
| `clave-jwt` | variable `JWT_CLAVE` | Al menos 32 caracteres |
| `clave-cifrado` | variable `CIFRADO_CLAVE` | 32 bytes en base64. Si se pierde, los datos cifrados no se recuperan |
| `clave-respuesta` | variable `RESPUESTA_CLAVE` | Al menos 32 caracteres. Firma los enlaces de respuesta del correo al contacto; si cambia, los ya enviados dejan de servir |
| `duracion-token` | 15 min | No puede superar `sesion.inactividad`: el backend no arranca si lo hace |
| `sesion.duracion-maxima` | 10 h | Desde el login hasta volver a pedir la contraseña; renovar no la alarga |
| `sesion.inactividad` | 15 min | Sin renovar en ese plazo, la sesión termina |
| `sesion.gracia-rotacion` | 10 s | Tiempo en que se acepta un token recién reemplazado |
| `sesion.cookie-segura` | `true` (`false` en `dev`) | Con `true`, la cookie de renovación solo viaja por HTTPS |
| `bloqueo.intentos` / `bloqueo.ventana` | 5 / 15 min | |
| `activacion.url` | `${DASHBOARD_URL}/activar` | Por defecto, `http://localhost:5173/activar` |
| `activacion.vigencia` | 48 h | |

Sin las claves, el backend no arranca. El perfil `dev` trae claves de desarrollo y lee la base desde el `.env`.

`server.forward-headers-strategy=native` hace que el backend tome la IP real de `X-Forwarded-For`, que nginx reemplaza con la dirección de quien se conecta. Solo se le cree a un proxy de la red interna, así que en producción el puerto del backend no debe quedar expuesto: la entrada debe ser nginx.

`docker-compose.yml` levanta el backend (`app/Dockerfile`) con el perfil `dev`. Recibe las credenciales por variables de entorno, y `SPRING_DATASOURCE_URL` reemplaza la dirección del perfil porque dentro de Docker la base está en `postgres` y no en `localhost`. El dashboard (`frontend/Dockerfile`) se sirve con nginx, que reenvía `/api` al backend (`frontend/nginx.conf`). Mailpit recibe los correos del backend y los muestra en http://localhost:8025, sin enviarlos. Esta configuración es solo para desarrollo y demos: producción necesita sus propias claves y no debe cargar los datos de ejemplo.

Valores base del Anexo B (`alertacuida.clinica.*`), **provisionales** hasta que los valide el equipo clínico. Cambiarlos cambia la configuración de todos los relojes que no tienen ajustes propios:

| Propiedad | Valor |
|---|---|
| `umbral-fc.grave-bajo` / `normal-min` / `normal-max` / `grave-alto` | 40 / 50 / 110 / 130 lpm |
| `umbral-spo2.grave-bajo` / `normal-min` | 88 / 92 % |
| `minutos-espera` | 5 |
| `minutos-tendencia` | 120 (mínimo 30, el intervalo de las lecturas) |
| `severidad-tendencia` | `MEDIA` (`LEVE`, `MEDIA` o `GRAVE`) |
| `alertacuida.alertas.avisar-desde` / `ventana-minutos` | 10 / 60: desde la 10.ª alerta del mismo reloj en 60 min se crea `EXCESO_ALERTAS` |
| `alertacuida.vigilancia.intervalo` | `PT5M`: cada cuánto corre el vigilante (formato ISO-8601) |
| `alertacuida.vigilancia.silencio` | 4h: tiempo sin sincronizar para crear `SILENCIO_DATOS` |
| `alertacuida.vigilancia.bateria-minima` | 20: bajo este % en 2 sincronizaciones seguidas se crea `BATERIA_BAJA` |
| `alertacuida.vigilancia.hora-inicio-dia` / `hora-fin-dia` | 8 / 22: horario (Chile) en que cuentan las sincronizaciones para `RELOJ_NO_PUESTO` |
| `alertacuida.correo.canal` | `SIMULADO` (variable `CORREO_CANAL`): `SMTP` envía de verdad |
| `alertacuida.correo.remitente` | Cuenta institucional (variable `CORREO_REMITENTE`) |
| `alertacuida.correo.intervalo` / `ventana` | `PT30S` / `PT1H`: cada cuánto se encola y envía, y hasta qué tan atrás se busca |
| `alertacuida.correo.reintentos` | `PT1M,PT5M,PT15M`: espera antes de cada reintento |
| `spring.mail.*` | Servidor SMTP (variables `SMTP_*`); por defecto, Mailpit en `localhost:1025` |

### Regla de tendencia: dónde cambiar cada parte

Todos estos valores son provisionales hasta que los valide el equipo clínico. La regla se evalúa en el reloj: lo que no viaja en la configuración también hay que cambiarlo en su código.

| Qué | Hoy | Dónde se cambia |
|---|---|---|
| Tiempo fuera del rango normal | 120 minutos | `alertacuida.clinica.minutos-tendencia` en `app/src/main/resources/application.properties`. Requiere reiniciar el backend; los relojes lo descargan solos |
| Gravedad de la alerta | `MEDIA` | `alertacuida.clinica.severidad-tendencia` en `app/src/main/resources/application.properties`. Requiere reiniciar el backend; los relojes la descargan solos |
| Reinicio del tiempo | Parte de cero si una lectura vuelve al rango normal | Código del reloj y los mismos documentos |
| Repetición de la alerta | Una sola vez mientras el valor siga en la zona intermedia; puede volver a alertar después de una lectura normal (05-11-2026) | Código del reloj y docs/api-reloj.md (pregunta 8) |
| Límites de cada paciente | Los propios, o los valores base si no tiene (FC 40, 50, 110 y 130; SpO2 88 y 92) | Por paciente: su ficha en el dashboard. Valores base: `alertacuida.clinica.umbral-*` en `application.properties`. Los relojes los descargan solos |
| Valores justo en el límite | `normalMin` y `normalMax` son normales; `graveBajo` y `graveAlto` son zona intermedia | Definición en `domain/.../clinica/Umbral.java` y código del reloj |
| Activación | Activa por defecto; se apaga por paciente en su ficha | Ficha del usuario. Valor por defecto: `Modulos.POR_DEFECTO` y una migración nueva sobre `configuracion` |

`Umbral` llama `LEVE` a una lectura de la zona intermedia. Eso describe la lectura; la alerta de tendencia que genera es `MEDIA`.

## Pruebas

- `RunTest`, `PoliticaContrasenaTest`, `UmbralTest`, `MediosDeContactoTest`, `HorarioYCoberturaTest`, `IniciarSesionTest`, `RenovarSesionTest`, `AltaYActivacionTest`, `PacientesTest`, `CondicionesPacienteTest`, `PerfilClinicoPacienteTest`, `ContactosPacienteTest`, `VinculoRelojTest`, `RecibirSincronizacionTest`, `RecibirAlertaTest`, `PlantillasCorreoTest`, `EncolarCorreosTest`, `EnviarCorreosPendientesTest`, `ResponderAvisoTest`, `FirmaRespuestaHmacTest`, `CifradoCamposTest` y `RegistroDeLecturasTest`: unitarias, sin base de datos. Los dobles en memoria compartidos están en `application/src/test/.../soporte/`.
- `InicioSesionTest`, `AuditoriaTest`, `IpRealTest`, `CuentasPersonalTest`, `PacientesYRelojesTest`, `PerfilYContactosTest`, `ApiRelojTest` y `ModeloDatosTest`: contra un PostgreSQL real en Docker (Testcontainers). Sin Docker, se omiten.
- `RequisitosCorreoTest`: el correo contra PostgreSQL real y GreenMail, un servidor SMTP de prueba en memoria (puerto 3025): ningún correo sale a internet. Sin Docker se omite.
- `RequisitosRespuestaContactoTest`: la respuesta del contacto por las rutas públicas, contra PostgreSQL real, con el token sacado del texto del correo. Sin Docker se omite.

## Duración de la sesión

El token de acceso dura 15 minutos y el dashboard lo guarda solo en memoria. Para no pedir la contraseña cada vez, el login deja un token de renovación en una cookie `HttpOnly`, que el código del navegador no puede leer. Al recargar la página, el dashboard pide tokens nuevos con esa cookie.

- **Límite de 10 horas:** la sesión termina a las 10 horas del login, aunque se esté usando. Renovar conserva `fecha_expiracion`.
- **Inactividad de 15 minutos:** el dashboard la mide con el mouse, el teclado y el desplazamiento (`useControlInactividad`), renueva antes del plazo mientras se usa y cierra la sesión al cumplirse, aunque el primer movimiento tras despertar el equipo llegue antes de la revisión periódica. Si una renovación falla por la red, la reintenta a los 3 segundos, dentro de la gracia. El servidor lo exige por su cuenta: rechaza un token cuya `fecha_emision` tenga 15 minutos o más. Por eso, un equipo suspendido tampoco puede seguir.
- **Rotación:** cada token sirve una vez (`RenovarSesion`). El reemplazado se marca en `fecha_revocacion`.
- **Reutilización:** si llega un token ya reemplazado, alguien pudo haberlo copiado, así que se borran todas las sesiones de la cuenta y se registra `TOKEN_REUTILIZADO`. La excepción son los 10 segundos de gracia, porque dos pestañas pueden renovar a la vez con la misma cookie.
- **Limpieza:** `TareasProgramadas` borra una vez al día, a las 04:00 de Chile, los tokens vencidos de todas las cuentas.
- **Cuenta desactivada:** no puede renovar, así que pierde el acceso en un máximo de 15 minutos.
- **Cierre de sesión:** borra el token de este navegador; las sesiones de otros computadores siguen abiertas. El token de acceso ya emitido no se puede revocar, pero el dashboard lo olvida y dura pocos minutos.

## Auditoría

`registro_auditoria` guarda quién hizo qué y cuándo, con la IP, usando solo identificadores y nunca datos personales. El administrador lo consulta con `GET /api/auditoria` (`ADMINISTRACION:VER`), en la pestaña Auditoría.

| Qué | Cómo queda |
|---|---|
| Inicios de sesión | `LOGIN` y `LOGIN_FALLIDO`, sin el RUN escrito |
| Posible robo de sesión | `TOKEN_REUTILIZADO`, con la cuenta afectada y la IP de quien usó el token. No cuenta para el bloqueo |
| Cambios | `CREAR`, `MODIFICAR` y `ELIMINAR`, escritos por cada caso de uso |
| Ficha del paciente | `LEER` con el id del paciente y la entidad `PACIENTE`, `CONDICION_PACIENTE`, `CONTACTO_PACIENTE`, `CONFIGURACION` o `ASIGNACION_RELOJ` (estado de sincronización del reloj) |
| Listas de pacientes y relojes | Una fila `LEER` por consulta, con `id_entidad = 'LISTA'` |

- **Las lecturas se registran después de leer con éxito:** cada endpoint se marca con `@LecturaAuditada` y `RegistroDeLecturas` llama a `RegistrarLectura` justo antes de escribir la respuesta. Un 404 o un 403 no entregó datos. Si no se puede registrar, la petición falla y los datos no salen. Un endpoint nuevo con datos de pacientes debe llevar esa anotación.
- **Significado de `id_entidad`:** en `LEER` es el id del paciente o `LISTA`, y la pestaña Auditoría lo rotula como "Usuario del programa" u "Objeto". En las demás acciones es el id del objeto cambiado.
- **La consulta no trae las lecturas por defecto**, porque son muchas más que los cambios. Se piden con `incluirLecturas=true`.
- **No se registran:** las renovaciones, los cierres de sesión ni lo que escribe el reloj, que queda en sus propias tablas.
- **Exportaciones:** aún no existen. Al programar los reportes deben registrar `EXPORTAR`.

## Pendiente del login

- Listar, editar y desactivar cuentas del personal (hoy solo se crean, desde Administración en el dashboard).
- Enviar el enlace por correo cuando exista.
- Avisos por correo a la oficina PRLAC: la regla está definida en el cronograma (2.10.5), pero el envío aún no existe; hoy el servidor solo guarda la alerta.
- Segundo factor TOTP: pospuesto por decisión del equipo (03-11-2026); revisarlo como riesgo en la revisión de seguridad.
- Agrupar los tokens por sesión con una tabla `SESION`: pospuesto el 05-11-2026. Hasta entonces, cerrar sesión borra solo el token actual, un posible robo cierra todas las sesiones de la cuenta, y una renovación atrasada puede cerrar un login nuevo.
