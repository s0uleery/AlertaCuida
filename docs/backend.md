# Backend: qué hace el código

Resumen del backend a la fecha (22-10-2026). Hoy hace cuatro cosas:
- crea la base de datos;
- permite iniciar sesión al personal;
- permite al administrador crear cuentas del equipo, que cada persona activa eligiendo su contraseña;
- administra los pacientes (en el dashboard, "usuarios"), los relojes y el vínculo entre ambos.

Cómo levantarlo: [README](../README.md). Cada clase tiene Javadoc con el detalle.

## Capas (arquitectura hexagonal)

Un módulo Maven por capa. Cada capa solo conoce a la de adentro.

| Módulo | Qué contiene | Depende de |
|---|---|---|
| `domain` | Reglas puras del negocio. Hoy: `Run` (valida el dígito verificador y normaliza el RUN a `12345678-5`), `ZonaHoraria`, `PoliticaContrasena` y los valores posibles de sexo, nivel de dependencia y estado del reloj. | Nada |
| `application` | Casos de uso y los puertos (interfaces) que necesitan. Java puro, sin Spring. | `domain` |
| `adapters` | Implementa los puertos: base de datos (JDBC), seguridad (bcrypt, HMAC, JWT) y controladores REST. Incluye las migraciones de Flyway. | `application` |
| `app` | Arranque de Spring Boot y las clases `Configuracion…`, el único lugar que une los casos de uso con sus adaptadores. | Todos |

Así, las reglas del login se prueban sin base de datos ni Spring (`IniciarSesionTest`). Cambiar, por ejemplo, JDBC por otra cosa solo toca `adapters`.

## Carpetas

Dentro de cada capa, una carpeta por funcionalidad: `sesion`, `personal` (cuentas del equipo), `paciente`, `reloj`, `asignacion` (vínculo reloj-paciente) y `auditoria`. Cada una tiene estas subcarpetas:

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

## Autorización por permiso (1.3.7)

Hay dos niveles:
1. `ConfiguracionSeguridad` exige un token válido en todo, salvo `login` y `activar`. Sin token, la respuesta es **401**.
2. Cada endpoint exige su permiso con `@PreAuthorize(Permisos.X)`. Sin el permiso, la respuesta es **403**. Las reglas están juntas en `adapters/web/Permisos.java`, para no escribirlas a mano en cada controlador.

Importante: el módulo `USUARIOS` son los **pacientes**. Las cuentas del personal van en `ADMINISTRACION`, que solo tiene el administrador.

## Cuentas del personal (1.3.2)

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
2. El enlace es `http://…/activar#<token>` y vence en 48 h. Mientras no exista el correo (2.4), el administrador lo copia y se lo pasa a la persona.
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
- **"No puede ser el RUN" se revisa con el hash**: no hace falta leer el RUN guardado, y seguirá funcionando cuando esté cifrado.
- **Un enlace sirve una sola vez, aunque se use en dos pestañas a la vez**: las actualizaciones tienen condiciones (`WHERE fecha_uso IS NULL`, `WHERE hash_contrasena IS NULL`).
- **Transacciones sin Spring en los casos de uso**: usan el puerto `Transacciones`, implementado con `TransactionTemplate` en `adapters`.

## Pacientes y relojes (1.2)

En el dashboard, a los pacientes se les llama "usuarios" (término del PRLAC); en el código y la base, "pacientes". Todo usa los permisos del módulo `USUARIOS`, que tienen el coordinador y el administrador.

| Endpoint | Permiso | Qué hace |
|---|---|---|
| `GET /api/pacientes?incluirBajas=` | `VER` | Lista con edad y reloj actual. |
| `GET /api/pacientes/{id}` | `VER` | Ficha con el historial de relojes. |
| `POST /api/pacientes` | `CREAR` | Alta (identidad). |
| `PUT /api/pacientes/{id}` | `EDITAR` | Edición, incluido el RUN si se escribió mal. |
| `POST /api/pacientes/{id}/baja` | `ELIMINAR` | Baja con fecha de hoy; cierra su reloj. No borra nada. |
| `POST /api/pacientes/{id}/reloj` | `EDITAR` | Vincula un reloj; devuelve su token (201). |
| `POST /api/pacientes/{id}/reloj/reemplazo` | `EDITAR` | Cambia el reloj por otro; el anterior puede quedar "en reparación". |
| `DELETE /api/pacientes/{id}/reloj` | `EDITAR` | Retira el reloj; su token deja de servir. |
| `POST /api/pacientes/{id}/reloj/token` | `EDITAR` | Otro token para el mismo reloj. |
| `GET /api/relojes` | `VER` | Inventario con el paciente que usa cada reloj. |
| `POST /api/relojes` / `PUT /api/relojes/{id}` | `CREAR` / `EDITAR` | Registro y edición (modelo, SIM, plan, estado). |

Reglas (1.2.5), en `VinculoReloj`, `EditarPaciente` y `EditarReloj`:

- Solo se vinculan relojes `OPERATIVO` a pacientes **con consentimiento** y **sin baja**.
- Un reloj con un solo paciente a la vez, y un paciente con un solo reloj. Si dos personas vinculan a la vez, la base lo impide igual (restricciones `EXCLUDE` de V4).
- El reemplazo cierra el vínculo anterior y abre el nuevo **en el mismo instante**, así el paciente no queda sin reloj.
- No se puede quitar el consentimiento a quien tiene reloj, ni sacar de servicio un reloj en uso: primero hay que desvincularlo.
- La baja cierra el vínculo en la misma transacción.

Decisiones:

- **El historial nunca se pierde**: los vínculos se cierran con fecha de fin, no se borran. Así se sabe qué reloj mandó cada dato.
- **Token del reloj (rol DEVICE)**: 256 bits aleatorios, se muestran una sola vez y en la base va su SHA-256, igual que el enlace de activación. Cerrar el vínculo lo revoca. El uso del token por el reloj llega en 1.4.1.
- **La edad no se guarda**: se calcula con la fecha de hoy en Chile (`ZonaHoraria`).
- **Datos personales sin cifrar todavía**: RUN, nombres, apellidos, fecha de nacimiento y dirección se guardan tal cual (con `TODO 1.6.3` en `PacientesJdbc`). Cuando llegue el cifrado, solo cambia ese adaptador. Por eso la lista se ordena en Java y la búsqueda se hace en el dashboard: la base no podrá ordenar ni buscar datos cifrados.
- La ficha de salud, las condiciones y los documentos quedan para la 3.3.

## Configuración

En `app/src/main/resources/application.properties`:

| Propiedad | Valor | Nota |
|---|---|---|
| `clave-run` | variable `RUN_CLAVE` | Si cambia, hay que recalcular el `rut_hash` de todos los usuarios |
| `clave-jwt` | variable `JWT_CLAVE` | Al menos 32 caracteres |
| `duracion-token` | 15 min (8 h en `dev`) | |
| `bloqueo.intentos` / `bloqueo.ventana` | 5 / 15 min | |
| `activacion.url` | `${DASHBOARD_URL}/activar` | Por defecto, `http://localhost:5173/activar` |
| `activacion.vigencia` | 48 h | |

Sin las claves, el backend no arranca. El perfil `dev` trae claves de desarrollo y lee la base desde el `.env`.

## Pruebas

- `RunTest`, `PoliticaContrasenaTest`, `IniciarSesionTest`, `AltaYActivacionTest`, `PacientesTest` y `VinculoRelojTest`: unitarias, sin base de datos. Los dobles en memoria compartidos están en `application/src/test/.../soporte/`.
- `InicioSesionTest`, `CuentasPersonalTest`, `PacientesYRelojesTest` y `ModeloDatosTest`: contra un PostgreSQL real en Docker (Testcontainers). Sin Docker, se omiten.

## Pendiente del login

- Listar, editar y desactivar cuentas del personal (hoy solo se crean, desde Administración en el dashboard).
- Enviar el enlace por correo cuando exista (2.4).
- Token de renovación con cookie y cierre de sesión (1.3.4).
- Segundo factor TOTP (1.3.5).
- Cifrar el `rut` en la base (1.6.3).
