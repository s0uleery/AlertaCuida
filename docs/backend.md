# Backend: qué hace el código

Resumen del backend a la fecha (22-10-2026). Hoy hace cuatro cosas:
- crea la base de datos;
- permite iniciar sesión al personal;
- permite al administrador crear cuentas del equipo, que cada persona activa eligiendo su contraseña;
- administra los pacientes (en el dashboard, "usuarios"), los relojes y el vínculo entre ambos.

Cómo levantarlo: [README](../README.md). Cada clase tiene Javadoc con el detalle.

Referencia rápida de los endpoints disponibles, con ejemplos JSON: [para el dashboard](endpoints-dashboard.md) y [para el reloj](endpoints-reloj.md). En formato OpenAPI: [api-dashboard.yaml](api-dashboard.yaml) y [api-reloj.yaml](api-reloj.yaml).

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

Dentro de cada capa, una carpeta por funcionalidad: `sesion`, `personal` (cuentas del equipo), `paciente`, `condicion` (enfermedades y situaciones del paciente), `clinica` (perfil clínico: módulos y umbrales), `contacto` (contactos de cada paciente), `reloj` (inventario), `asignacion` (vínculo reloj-paciente), `telemetria` (lotes que envía el reloj), `alerta`, `calendario` (horario hábil, feriados y enrutamiento por gravedad) y `auditoria`. Los controladores y la seguridad de la API del reloj van en `adapters/web/dispositivo` y `adapters/seguridad/dispositivo`. Cada una tiene estas subcarpetas:

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

Con el perfil `dev` se carga además `db/ejemplo/R__datos_de_ejemplo.sql`, con el administrador de prueba. El detalle de las tablas está en [modelo-datos.md](modelo-datos.md).

## Inicio de sesión

Endpoints:

- `POST /api/auth/login`: recibe `{run, contrasena}` y devuelve el token, su vencimiento y el usuario con sus permisos. Es lo único que se puede llamar sin token.
- `GET /api/auth/yo`: devuelve el usuario del token. El front lo usa al recargar la página.

Recorrido de un login:

1. `SesionController` recibe la petición. Solo traduce HTTP; las reglas están en el caso de uso.
2. `IniciarSesion` normaliza el RUN y le calcula el hash (`HashRunHmac`). Con ese hash busca al usuario (`CuentaUsuarioJdbc`), sin tener que descifrar los RUN guardados.
3. Cuenta los intentos fallidos recientes en `registro_auditoria` (`RegistroAccesosJdbc`). La cuenta queda bloqueada con 5 fallos en 15 minutos sin un login correcto entre medio.
4. Compara la contraseña con su hash bcrypt (`VerificadorBcrypt`).
5. Si todo está bien, registra `LOGIN` y entrega un token JWT (`TokenJwt`) con el id, el nombre, el rol y los permisos (`"ALERTAS:VER"`, …). Si algo falla, registra `LOGIN_FALLIDO` y responde 401.

Después, cada petición manda el token en la cabecera `Authorization: Bearer …`. Spring Security revisa su firma y su vencimiento, y convierte los permisos en *authorities*. El servidor no guarda sesiones.

Decisiones de seguridad:

- **Mismo mensaje para cualquier falla** ("RUN o contraseña incorrectos"), y el mismo tiempo de respuesta: si el RUN no existe, igual se ejecuta bcrypt con un hash de relleno. Así no se puede averiguar qué RUN están registrados.
- **El hash del RUN lleva clave (HMAC-SHA256)**: los RUN posibles son pocos, así que un SHA-256 simple se podría revertir probándolos todos.
- **El bloqueo se calcula desde la auditoría**: no hay un contador que mantener ni que se pueda desincronizar.
- **Sin CSRF**: el token no va en una cookie, así que el navegador no lo manda solo.
- **Los permisos viajan en el token**: si cambian, se notan al iniciar sesión de nuevo.
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

1. El administrador escribe el RUN, el nombre, el cargo, el correo y el rol. `CrearCuentaPersonal`:
   - valida el RUN y el rol;
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
| `PUT /api/pacientes/{id}/perfil-clinico/ajustes` | `EDITAR` | Módulos, GPS, modo conservador, minutos de espera y motivo. |
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
- Sin configuración rigen los valores por defecto de V3 (todos los módulos menos la tendencia; GPS y modo conservador apagados). Los minutos de espera van de 1 a 60; vacíos = el valor base.

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
- **`ReglaEnrutamiento`** (aplicación), fija en el código: una alerta leve fuera de horario o en feriado espera al día hábil; en cualquier otro caso se avisa a los contactos activos, con algún canal y cuyo horario cubre ese momento, por prioridad. El horario de cada contacto se respeta siempre, también en horario hábil.
- **Quién la usa**: el reloj, porque escala (recibirá el calendario con la configuración, 1.7); el servidor, para revisar el `esperaDiaHabil` de las alertas que recibe (2.1.2) y para sus alertas técnicas (2.2).

## API del reloj

Lo que llama el reloj (o el simulador), según el contrato de [api-reloj.md](api-reloj.md). Implementado: sincronización, alertas, cancelaciones y estado de una alerta. Falta: configuración y token de Firebase (1.7), agravamiento (2.1.3), solicitud de aviso y fin del escalamiento (2.10), acuse de avisos FCM (2.3) y webhook de WhatsApp (2.9).

| Endpoint | Caso de uso | Guarda en |
|---|---|---|
| `POST /api/reloj/v1/sincronizaciones` | `RecibirSincronizacion` | `sincronizacion`, `registro_vital`, `ubicacion`, `evento_descartado` |
| `POST /api/reloj/v1/alertas` | `RecibirAlerta.recibir` | `alerta` (origen `RELOJ`, evidencia en `jsonb`) |
| `POST /api/reloj/v1/cancelaciones` | `RecibirAlerta.registrarCancelacion` | `alerta` con `fecha_hora_cancelacion` |
| `GET /api/reloj/v1/alertas/{eventId}` | `RecibirAlerta.estado` | — (lee la vista `alerta_estado`) |

**Autenticación (cadena de seguridad aparte).** `/api/reloj/**` tiene su propia `SecurityFilterChain`, que va antes que la del dashboard. Solo acepta el token del reloj: Spring lo trata como token "opaco" y `IntrospectorTokenReloj` lo busca por su SHA-256 entre los vínculos **vigentes** (`AutenticarReloj`). Un token de un vínculo cerrado, desconocido o ausente responde 401 `TOKEN_INVALIDO`. El token del reloj no abre el dashboard (no es un JWT) y el del dashboard no abre la API del reloj. El controlador recibe el vínculo (`PrincipalReloj`), nunca un id de paciente enviado por el reloj.

Reglas:

- **Idempotencia:** `INSERT … ON CONFLICT DO NOTHING` por `event_id`. Un reenvío devuelve lo guardado la primera vez (`loteRepetido: true`; la alerta, con 200 en vez de 201). Dos reenvíos simultáneos tampoco duplican. Un `eventId` de otro reloj responde 409.
- **Lecturas:** se descartan una por una (sin rechazar el lote) si están fuera del rango del sensor (FC de 20 a 250, SpO2 de 50 a 100, en `TipoLectura`), si son futuras (más de 5 min) o anteriores a la entrega del reloj. Las repetidas (misma asignación, tipo y hora) las ignora la base. Se insertan en un solo viaje (batch).
- **Alertas:** nunca se rechazan por su contenido, solo si están mal formadas. Una media o grave marcada "espera día hábil" se guarda sin la marca (esas avisan siempre). `SILENCIO_DATOS` no lo envía el reloj.
- **Errores:** `ErroresApiReloj` responde en RFC 9457 con `codigo` (`MENSAJE_INVALIDO`, `ALERTA_DESCONOCIDA`, `NO_APLICA`, `LOTE_GRANDE`), solo para los controladores del reloj.
- **Hora del reloj:** si difiere más de 120 s de la del servidor, se acepta igual y la respuesta informa `desfaseRelojSegundos`.
- **Sin auditoría:** lo que escribe el reloj no va a `registro_auditoria`; queda en sus propias tablas con la hora de recepción.

## Cifrado de datos personales

Los adaptadores JDBC cifran al guardar y descifran al leer con `CifradoCampos`; los casos de uso no se enteran. Las columnas cifradas que ya usa el código están en `ColumnaCifrada`:

| Tabla | Columnas |
|---|---|
| `usuario` | `rut` |
| `paciente` | `rut`, `nombres`, `apellidos`, `fecha_nacimiento`, `direccion` |
| `condicion_paciente` | `descripcion` |
| `contacto` | `nombre`, `whatsapp`, `correo` |
| `configuracion` | `motivo_cambio` |

Las demás columnas marcadas "cifrado" en las migraciones (`usuario.telefono` y `totp_secreto`, notas, observaciones) se agregan a `ColumnaCifrada` cuando se programe su adaptador.

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
| `duracion-token` | 15 min (8 h en `dev`) | |
| `bloqueo.intentos` / `bloqueo.ventana` | 5 / 15 min | |
| `activacion.url` | `${DASHBOARD_URL}/activar` | Por defecto, `http://localhost:5173/activar` |
| `activacion.vigencia` | 48 h | |

Sin las claves, el backend no arranca. El perfil `dev` trae claves de desarrollo y lee la base desde el `.env`.

Valores base del Anexo B (`alertacuida.clinica.*`), **provisionales** hasta que los valide el equipo clínico. Cambiarlos cambia la configuración de todos los relojes que no tienen ajustes propios:

| Propiedad | Valor |
|---|---|
| `umbral-fc.grave-bajo` / `normal-min` / `normal-max` / `grave-alto` | 40 / 50 / 110 / 130 lpm |
| `umbral-spo2.grave-bajo` / `normal-min` | 88 / 92 % |
| `minutos-espera` | 5 |

## Pruebas

- `RunTest`, `PoliticaContrasenaTest`, `UmbralTest`, `MediosDeContactoTest`, `HorarioYCoberturaTest`, `IniciarSesionTest`, `AltaYActivacionTest`, `PacientesTest`, `CondicionesPacienteTest`, `PerfilClinicoPacienteTest`, `ContactosPacienteTest`, `VinculoRelojTest`, `RecibirSincronizacionTest`, `RecibirAlertaTest` y `CifradoCamposTest`: unitarias, sin base de datos. Los dobles en memoria compartidos están en `application/src/test/.../soporte/`.
- `InicioSesionTest`, `CuentasPersonalTest`, `PacientesYRelojesTest`, `PerfilYContactosTest`, `ApiRelojTest` y `ModeloDatosTest`: contra un PostgreSQL real en Docker (Testcontainers). Sin Docker, se omiten.

## Pendiente del login

- Listar, editar y desactivar cuentas del personal (hoy solo se crean, desde Administración en el dashboard).
- Enviar el enlace por correo cuando exista.
- Token de renovación con cookie y cierre de sesión.
- Segundo factor TOTP.
