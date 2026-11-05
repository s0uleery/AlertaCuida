# Pruebas del modelo de datos

Informe generado automáticamente por [`pruebas-modelo-datos/prueba-modelo.mjs`](pruebas-modelo-datos/prueba-modelo.mjs). No editar a mano: se reescribe en cada ejecución.

| | |
|---|---|
| Fecha | 5 de noviembre de 2026 a las 7:43 p. m. |
| Modelo | [`modelo-datos.dbml`](modelo-datos.dbml) + [`reglas-extra.sql`](pruebas-modelo-datos/reglas-extra.sql) |
| Base de datos | PostgreSQL embebido (PGlite 0.2.17), conversión con @dbml/core 3.14.1, Node v26.11.1 |
| **Resultado** | **299 casos: 285 ✅ · 0 ❌ · 0 ⚠️ · 14 ℹ️** |

## Cómo repetir las pruebas

```bash
cd docs/pruebas-modelo-datos
npm install
npm test
```

Cada ejecución crea la base desde cero en memoria, con datos inventados (pacientes María, Pedro y Rosa; profesionales Ana y Luis), y no toca ninguna base real. El grupo A usa una base aparte para revisar las relaciones uno a uno. Si hay casos ❌, el comando termina con error.

**Cómo leer los resultados:**
- ✅ **Se acepta / Se rechaza (motivo) / = valor:** la base hace lo que dicen las notas del modelo.
- ❌ La base no hace lo que dicen las notas del modelo: hay que corregirlo.
- ⚠️ **Hueco:** la base permite algo que no debería y las notas no lo cubren. Ver la recomendación al final.
- ℹ️ **Lo valida el backend:** la base lo permite a propósito; la nota del modelo dice que lo controla el backend.

## Resumen por grupo

| Grupo | Casos | ✅ | ❌ | ⚠️ | ℹ️ |
|---|---|---|---|---|---|
| A. Relaciones uno a uno (en una base aparte) | 6 | 6 | 0 | 0 | 0 |
| 1. Estructura de la base | 6 | 6 | 0 | 0 | 0 |
| 2. Roles, permisos y profesionales | 31 | 28 | 0 | 0 | 3 |
| 3. Inicio de sesión calculado desde la auditoría | 8 | 8 | 0 | 0 | 0 |
| 4. Pacientes y datos de salud | 19 | 19 | 0 | 0 | 0 |
| 5. Contactos (compartidos, exclusivos y horarios) | 23 | 23 | 0 | 0 | 0 |
| 6. Configuración clínica y umbrales (historiales separados) | 30 | 30 | 0 | 0 | 0 |
| 7. Relojes y asignaciones | 23 | 20 | 0 | 0 | 3 |
| 8. Datos recibidos del reloj (lotes, lecturas, GPS) | 19 | 19 | 0 | 0 | 0 |
| 9. Configuración pendiente en el reloj (por hash) | 5 | 5 | 0 | 0 | 0 |
| 10. Flujo completo de una alerta (caída de María) | 36 | 34 | 0 | 0 | 2 |
| 11. Reglas de la atención (ATENCION_ALERTA) | 13 | 13 | 0 | 0 | 0 |
| 12. Gravedad y horario (leve, media y grave de madrugada) | 18 | 18 | 0 | 0 | 0 |
| 13. Cancelación por el paciente y simulacro | 7 | 5 | 0 | 0 | 2 |
| 14. Alertas técnicas del servidor | 15 | 14 | 0 | 0 | 1 |
| 15. Reintentos y mensajes duplicados (idempotencia) | 7 | 7 | 0 | 0 | 0 |
| 16. Reglas de los avisos a contactos | 9 | 6 | 0 | 0 | 3 |
| 17. Datos calculados (reportes, hallazgos, panel) | 9 | 9 | 0 | 0 | 0 |
| 18. Auditoría de cambios | 4 | 4 | 0 | 0 | 0 |
| 19. Borrado al terminar el piloto (4.4.5) | 11 | 11 | 0 | 0 | 0 |

## Detalle

### A. Relaciones uno a uno (en una base aparte)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 1 | La clave foránea de la ficha está en FICHA_PACIENTE (apunta a PACIENTE) | = 1 | = 1 | ✅ |
| 2 | La clave foránea de la atención está en ATENCION_ALERTA (apunta a ALERTA) | = 1 | = 1 | ✅ |
| 3 | Alta de un paciente sin ficha todavía | Se acepta | Aceptado | ✅ |
| 4 | Llega una alerta que nadie ha asumido (debe quedar SIN_ASIGNAR) | Se acepta | Aceptado | ✅ |
| 5 | Se borra la ficha de Pedro (por ejemplo, para rehacerla) | Se acepta | Aceptado | ✅ |
| 6 | Borrar la ficha no borra al paciente | = 1 | = 1 | ✅ |

### 1. Estructura de la base

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 7 | Cantidad de tablas | = 29 | = 29 | ✅ |
| 8 | Tablas sueltas (sin ninguna relación) | = 0 | = 0 | ✅ |
| 9 | Cantidad de relaciones (claves foráneas) | = 32 | = 32 | ✅ |
| 10 | Cantidad de tipos (Enum) | = 20 | = 20 | ✅ |
| 11 | Vistas de datos calculados | = 1 | = 1 | ✅ |
| 12 | Ninguna columna jsonb en la configuración clínica | = 0 | = 0 | ✅ |

### 2. Roles, permisos y profesionales

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 13 | Catálogo de roles (ADMINISTRADOR es de sistema) | Se acepta | Aceptado | ✅ |
| 14 | Permisos por rol, módulo y acción | Se acepta | Aceptado | ✅ |
| 15 | Alta de tres profesionales activos | Se acepta | Aceptado | ✅ |
| 16 | Ana puede editar alertas (permiso de su rol) | = 1 | = 1 | ✅ |
| 17 | Ana no puede ver reportes | = 0 | = 0 | ✅ |
| 18 | Permiso repetido | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 19 | Módulo que no existe (FINANZAS) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 20 | Permiso de un rol que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 21 | Profesional con un rol que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 22 | Borrar un rol que tiene profesionales | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 23 | Borrar un rol sin profesionales borra sus permisos (se deshace) | Se acepta | Aceptado | ✅ |
| 24 | Quitar todos los permisos del ADMINISTRADOR (rol de sistema) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 25 | Cuenta nueva creada por el administrador: inactiva, sin contraseña, con enlace de activación | Se acepta | Aceptado | ✅ |
| 26 | Activar la cuenta sin contraseña | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 27 | La usuaria define su contraseña: se activa y el enlace queda usado | Se acepta | Aceptado | ✅ |
| 28 | Dos profesionales con el mismo correo | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 29 | El mismo correo con otras mayúsculas (ANA@muni.cl): se guarda en minúsculas | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 30 | Desactivar a una usuaria con un enlace de activación pendiente | Lo valida el backend | Aceptado por la base | ℹ️ |
| 31 | Dos profesionales con el mismo RUN | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 32 | Coordinadora con teléfono y opt-in de WhatsApp (se deshace) | Se acepta | Aceptado | ✅ |
| 33 | Opt-in de WhatsApp sin teléfono | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 34 | Revocación del opt-in sin haberlo aceptado | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 35 | Coordinadora sin teléfono (no recibiría los avisos de la oficina) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 36 | Profesional sin RUN | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 37 | Enlace con un tipo que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 38 | Enlace repetido | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 39 | Token de sesión que vence antes de emitirse | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 40 | Enlace que vence al mismo tiempo que se emite | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 41 | Sesión y códigos de respaldo de Ana | Se acepta | Aceptado | ✅ |
| 42 | Código de respaldo repetido para la misma profesional | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 43 | Token de sesión repetido | Se rechaza (clave única) | Rechazado (clave única) | ✅ |

### 3. Inicio de sesión calculado desde la auditoría

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 44 | Tres intentos fallidos de Ana en 10 minutos | Se acepta | Aceptado | ✅ |
| 45 | Ana está bloqueada (3 fallidos en 15 min, valores del yml) | = true | = true | ✅ |
| 46 | El administrador la desbloquea y Ana entra | Se acepta | Aceptado | ✅ |
| 47 | Ya no está bloqueada | = false | = false | ✅ |
| 48 | Último acceso de Ana (hace 1 minuto) | = true | = true | ✅ |
| 49 | Intento fallido con un RUN que no existe (sin usuario) | Se acepta | Aceptado | ✅ |
| 50 | Auditoría con entidad pero sin su id | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 51 | Acción de auditoría que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |

### 4. Pacientes y datos de salud

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 52 | Alta de tres pacientes con consentimiento firmado | Se acepta | Aceptado | ✅ |
| 53 | Pacientes por evaluar (dependencia vacía) | = 1 | = 1 | ✅ |
| 54 | La fecha de nacimiento (cifrada) se guarda como texto; la edad la calcula el backend | = character varying | = character varying | ✅ |
| 55 | Ficha de María (uno a uno), dos enfermedades y dos situaciones | Se acepta | Aceptado | ✅ |
| 56 | Ficha de María: enfermedades / situaciones | = 2 / 2 | = 2 / 2 | ✅ |
| 57 | Segunda ficha para el mismo paciente | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 58 | Ficha de un paciente que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 59 | Condición sin descripción | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 60 | Condición sin indicar si es enfermedad o situación | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 61 | Tipo de condición que no existe (ALERGIA) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 62 | Ana sube el consentimiento de María | Se acepta | Aceptado | ✅ |
| 63 | Dos documentos en la misma ruta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 64 | Documento subido por un usuario que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 65 | Tipo de documento que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 66 | Paciente con RUN repetido | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 67 | Paciente sin fecha de ingreso | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 68 | Baja anterior al ingreso | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 69 | Sexo que no existe en el catálogo | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 70 | Paciente con rut_hash pero sin el RUN cifrado | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |

### 5. Contactos (compartidos, exclusivos y horarios)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 71 | Catálogo de tipos de contacto | Se acepta | Aceptado | ✅ |
| 72 | Cuatro personas de contacto (una solo con correo) | Se acepta | Aceptado | ✅ |
| 73 | Listas de contactos: Seguridad Ciudadana en los tres pacientes; la cuidadora de Pedro de noche (22:00–07:00) | Se acepta | Aceptado | ✅ |
| 74 | Seguridad Ciudadana está guardada una sola vez | = 1 | = 1 | ✅ |
| 75 | Seguridad Ciudadana es contacto de tres pacientes (compartido) | = 3 | = 3 | ✅ |
| 76 | La cuidadora de Pedro está disponible a las 03:00 (horario que cruza la medianoche) | = true | = true | ✅ |
| 77 | La cuidadora de Pedro no está disponible a las 15:00 | = false | = false | ✅ |
| 78 | Meta informa la baja del opt-in de un número: se encuentra a la persona por su hash | 1 fila(s) afectada(s) | 1 fila(s) afectada(s) | ✅ |
| 79 | Misma persona registrada dos veces (mismo WhatsApp) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 80 | Misma persona registrada dos veces (mismo correo) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 81 | Persona sin WhatsApp ni correo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 82 | WhatsApp sin su hash | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 83 | Correo sin su hash | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 84 | Baja del opt-in sin opt-in previo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 85 | La misma persona dos veces en la lista de un paciente | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 86 | Dos contactos activos con la misma prioridad | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 87 | Repetir prioridad con un contacto inactivo (se deshace) | Se acepta | Aceptado | ✅ |
| 88 | Dos cuidadores principales activos para el mismo paciente | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 89 | Prioridad 0 | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 90 | Horario con inicio pero sin fin | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 91 | Horario que empieza y termina a la misma hora | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 92 | Parentesco en un contacto que no es FAMILIA | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 93 | Tipo de contacto que no existe (AMBULANCIA) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |

### 6. Configuración clínica y umbrales (historiales separados)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 94 | María: configuración inicial y un cambio posterior (activa el GPS) | Se acepta | Aceptado | ✅ |
| 95 | Pedro con valores por defecto; Rosa en modo conservador | Se acepta | Aceptado | ✅ |
| 96 | Módulos por defecto (caídas, FC, SpO2, inactividad, tendencia y botón sí; GPS y zona no) | = true,true,true,true,true,false,false,true | = true,true,true,true,true,false,false,true | ✅ |
| 97 | Zona segura activa sin GPS | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 98 | Zona segura de Pedro: 200 m, media por defecto, coordenadas cifradas como texto | Se acepta | Aceptado | ✅ |
| 99 | La zona sin severidad elegida es media | = MEDIA | = MEDIA | ✅ |
| 100 | Pedro activa la zona con el GPS (se deshace) | Se acepta | Aceptado | ✅ |
| 101 | Zona segura con radio cero | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 102 | Zona segura con una severidad que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 103 | Dos zonas de Pedro con la misma fecha | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 104 | Configuración vigente de María (la más reciente): GPS activo | = true | = true | ✅ |
| 105 | Umbrales de María: FC (dos versiones) y SpO2 sin límite alto | Se acepta | Aceptado | ✅ |
| 106 | Historiales independientes: 2 configuraciones y 3 umbrales de María | = 2 / 3 | = 2 / 3 | ✅ |
| 107 | Umbral FC vigente de María: normal desde 55 | = 55 | = 55 | ✅ |
| 108 | Umbral FC que regía el 05-10 (versión del 01-10): normal desde 50 | = 50 | = 50 | ✅ |
| 109 | María vuelve al umbral base de SpO2 sin borrar historial (fila con las cuatro columnas vacías) | Se acepta | Aceptado | ✅ |
| 110 | Para SpO2 rige el umbral base | = true | = true | ✅ |
| 111 | Pedro no tiene umbrales propios (rige el base) | = 0 | = 0 | ✅ |
| 112 | Dos configuraciones con la misma fecha | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 113 | Minutos de espera en cero | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 114 | Dos umbrales de la misma variable con la misma fecha | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 115 | Normal mínimo mayor que el normal máximo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 116 | Grave bajo por encima del normal mínimo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 117 | Grave alto por debajo del normal máximo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 118 | Umbral de una variable que no existe (presión arterial) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 119 | Umbral de FC a medias (solo grave bajo y grave alto) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 120 | Umbral de FC sin límite alto | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 121 | Umbral de SpO2 con límite alto | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 122 | Umbral de SpO2 con normal desde 101 % | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 123 | Umbral de FC completo y coherente para Pedro (se deshace) | Se acepta | Aceptado | ✅ |

### 7. Relojes y asignaciones

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 124 | Registro de cinco relojes con su SIM (uno retirado) | Se acepta | Aceptado | ✅ |
| 125 | Dos relojes con la misma SIM (ICCID) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 126 | Dos relojes con el mismo identificador | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 127 | Reloj sin estado | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 128 | Relojes con el plan vencido al 20-10-2026 | = 0 | = 0 | ✅ |
| 129 | Asignación de un reloj a cada paciente | Se acepta | Aceptado | ✅ |
| 130 | Lote del reloj antiguo de Rosa | Se acepta | Aceptado | ✅ |
| 131 | Reemplazo del reloj de Rosa: se cierra la asignación y se abre otra | Se acepta | Aceptado | ✅ |
| 132 | Rosa conserva el historial: 2 asignaciones, 1 vigente | = 2/1 | = 2/1 | ✅ |
| 133 | El token del reloj antiguo ya no vale | = 0 | = 0 | ✅ |
| 134 | El lote antiguo sigue ligado a Rosa | = true | = true | ✅ |
| 135 | Relojes libres para asignar (operativos y sin asignación vigente) | = 1 | = 1 | ✅ |
| 136 | Un reloj asignado a dos pacientes a la vez | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 137 | Un paciente con dos relojes vigentes | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 138 | Reusar el token de una asignación cerrada | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 139 | Asignación sin token | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 140 | Asignación que termina antes de empezar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 141 | Una asignación cerrada del reloj antiguo de Rosa que se superpone con su historial (a otro paciente) | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 142 | Una asignación cerrada de Rosa que se superpone con sus relojes | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 143 | El reloj antiguo de Rosa se asigna a otro paciente desde el momento en que se cerró (se deshace) | Se acepta | Aceptado | ✅ |
| 144 | Asignar un reloj RETIRADO | Lo valida el backend | Aceptado por la base | ℹ️ |
| 145 | Asignar un reloj a un paciente sin consentimiento firmado | Lo valida el backend | Aceptado por la base | ℹ️ |
| 146 | Dar de baja a un paciente que tiene un reloj vigente | Lo valida el backend | Aceptado por la base | ℹ️ |

### 8. Datos recibidos del reloj (lotes, lecturas, GPS)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 147 | Lotes de cada reloj (LTE y Wi-Fi) | Se acepta | Aceptado | ✅ |
| 148 | Lecturas de FC y SpO2 con su hora de medición | Se acepta | Aceptado | ✅ |
| 149 | Lectura con una asignación distinta a la de su lote (redundancia controlada) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 150 | Segundo lote de María, que reenvía historial | Se acepta | Aceptado | ✅ |
| 151 | Lectura reenviada en otro lote | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 152 | El backend ignora la lectura reenviada (ON CONFLICT DO NOTHING) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 153 | Lectura de un tipo que no existe (presión arterial) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 154 | Lectura de un lote que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 155 | Ubicación GPS de María | Se acepta | Aceptado | ✅ |
| 156 | Ubicación repetida para la misma hora | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 157 | Ubicación con una asignación distinta a la de su lote | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 158 | Eventos descartados por el reloj, por tipo | Se acepta | Aceptado | ✅ |
| 159 | Contador de descartes en cero | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 160 | Contador de descartes repetido para el mismo tipo | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 161 | El reloj informa descartes de un evento que solo crea el servidor (SILENCIO_DATOS) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 162 | Lote repetido (mismo event_id, el reloj reintentó) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 163 | Batería de 150 % | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 164 | Minutos puesto negativos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 165 | Estado actual del reloj de María (último lote): batería | = 70 | = 70 | ✅ |

### 9. Configuración pendiente en el reloj (por hash)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 166 | Pedro: el reloj tiene h1 y el backend calcula h1 | = false | = false | ✅ |
| 167 | María cambió su configuración: el backend calcula h2 y el reloj sigue en h1 | = true | = true | ✅ |
| 168 | El reloj de María aplica la configuración nueva y la informa en su siguiente lote | Se acepta | Aceptado | ✅ |
| 169 | María al día | = false | = false | ✅ |
| 170 | Un reloj que aún no envía lotes queda pendiente | = true | = true | ✅ |

### 10. Flujo completo de una alerta (caída de María)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 171 | Llega una caída MEDIA del reloj, con su evidencia | Se acepta | Aceptado | ✅ |
| 172 | Estado inicial | = RECIBIDA / SIN_ASIGNAR | = RECIBIDA / SIN_ASIGNAR | ✅ |
| 173 | Se asigna un folio automáticamente | = true | = true | ✅ |
| 174 | Retraso por cobertura (recepción menos detección) se calcula | = true | = true | ✅ |
| 175 | El reloj pide avisar a la hija (prioridad 1) | Se acepta | Aceptado | ✅ |
| 176 | Estado al enviar el primer aviso | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 177 | Meta informa entrega y lectura (webhook) | Se acepta | Aceptado | ✅ |
| 178 | Estado de entrega calculado desde las fechas | = LEIDO | = LEIDO | ✅ |
| 179 | La hija responde "No puedo" | Se acepta | Aceptado | ✅ |
| 180 | Sigue avisando | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 181 | La caída se agrava por inmovilidad | Se acepta | Aceptado | ✅ |
| 182 | Conserva la gravedad con que llegó | = MEDIA | = MEDIA | ✅ |
| 183 | Gravedad actual calculada | = GRAVE | = GRAVE | ✅ |
| 184 | El reloj pasa a Seguridad Ciudadana (prioridad 2), que responde "Voy en camino" | Se acepta | Aceptado | ✅ |
| 185 | Estado tras la confirmación | = CONFIRMADA / SIN_ASIGNAR | = CONFIRMADA / SIN_ASIGNAR | ✅ |
| 186 | Aviso FCM al reloj y el reloj informa que lo recibió | Se acepta | Aceptado | ✅ |
| 187 | Contacto que confirmó (para la pantalla del reloj) | = Seguridad Ciudadana | = Seguridad Ciudadana | ✅ |
| 188 | Ana asume el caso | 1 fila(s) afectada(s) | 1 fila(s) afectada(s) | ✅ |
| 189 | Estado al asumir | = CONFIRMADA / EN_ATENCION | = CONFIRMADA / EN_ATENCION | ✅ |
| 190 | Luis intenta asumirlo un momento después: no afecta filas (ya lo tomó Ana) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 191 | La responsable sigue siendo Ana | = Ana | = Ana | ✅ |
| 192 | Ana la resuelve como POSITIVA y queda a cargo del seguimiento | Se acepta | Aceptado | ✅ |
| 193 | Estado tras el cierre | = RESUELTA / EN_SEGUIMIENTO | = RESUELTA / EN_SEGUIMIENTO | ✅ |
| 194 | Reasignación del seguimiento a Luis | Se acepta | Aceptado | ✅ |
| 195 | Catálogo de destinos de derivación | Se acepta | Aceptado | ✅ |
| 196 | Llamada y derivación al CESFAM | Se acepta | Aceptado | ✅ |
| 197 | "Derivada" se ve en el seguimiento (no es un estado) | = 1 | = 1 | ✅ |
| 198 | Derivación sin destino | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 199 | Llamada con destino de derivación | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 200 | Derivación a un destino que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 201 | Cierre del seguimiento antes del cierre de la alerta | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 202 | Cierre del seguimiento | Se acepta | Aceptado | ✅ |
| 203 | Estado final | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 204 | Agregar un seguimiento después del cierre del seguimiento | Lo valida el backend | Aceptado por la base | ℹ️ |
| 205 | Historial del caso (avisos + FCM + seguimientos) | = 5 | = 5 | ✅ |
| 206 | El equipo cambia la gravedad con que llegó la alerta | Lo valida el backend | Aceptado por la base | ℹ️ |

### 11. Reglas de la atención (ATENCION_ALERTA)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 207 | Media de madrugada (Rosa): avisa de inmediato a Seguridad Ciudadana (compartido) | Se acepta | Aceptado | ✅ |
| 208 | Atención vacía (sin toma ni cierre) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 209 | Toma sin responsable | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 210 | Responsable sin hora de toma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 211 | Clasificar sin cerrar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 212 | Observaciones de cierre sin cerrar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 213 | Cierre anterior a la toma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 214 | POSITIVA sin responsable del seguimiento | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 215 | Cierre de seguimiento en una falsa alarma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 216 | Responsable que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 217 | Clasificación que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 218 | Dos atenciones para la misma alerta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 219 | Atención de una alerta que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |

### 12. Gravedad y horario (leve, media y grave de madrugada)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 220 | Leve de madrugada (Pedro): el reloj no avisa y espera el día hábil | Se acepta | Aceptado | ✅ |
| 221 | Queda en la bandeja de día hábil | = PENDIENTE_DIA_HABIL / SIN_ASIGNAR | = PENDIENTE_DIA_HABIL / SIN_ASIGNAR | ✅ |
| 222 | El folio crece con cada alerta | = true | = true | ✅ |
| 223 | A primera hora, Ana la cierra directamente como falsa alarma (sin asumirla antes) | Se acepta | Aceptado | ✅ |
| 224 | Estado tras la revisión | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 225 | La media está avisando | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 226 | Grave de madrugada (Rosa): el reloj recorre la lista dos veces | Se acepta | Aceptado | ✅ |
| 227 | Nadie confirma: el reloj informa el fin de la lista | Se acepta | Aceptado | ✅ |
| 228 | Estado de la grave sin respuesta | = SIN_RESPUESTA / SIN_ASIGNAR | = SIN_RESPUESTA / SIN_ASIGNAR | ✅ |
| 229 | Una media marcada para esperar el día hábil | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 230 | Una grave marcada para esperar el día hábil | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 231 | Gravedad que no existe (MODERADA) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 232 | Un botón de ayuda que no es leve | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 233 | Botón de ayuda de madrugada: leve, espera el día hábil (se deshace) | Se acepta | Aceptado | ✅ |
| 234 | Salida de la zona segura con la gravedad de la zona (se deshace) | Se acepta | Aceptado | ✅ |
| 235 | Agravar una alerta que llegó grave | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 236 | Agravar una alerta que no es caída | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 237 | Tercera ronda de avisos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 13. Cancelación por el paciente y simulacro

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 238 | El paciente cancela una caída en los 30 segundos | Se acepta | Aceptado | ✅ |
| 239 | Estado de la cancelada | = CANCELADA / SIN_ASIGNAR | = CANCELADA / SIN_ASIGNAR | ✅ |
| 240 | Una cancelada no puede quedar sin respuesta | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 241 | Una cancelada recibe atención del equipo | Lo valida el backend | Aceptado por la base | ℹ️ |
| 242 | Una cancelada tiene avisos a contactos | Lo valida el backend | Aceptado por la base | ℹ️ |
| 243 | Simulacro desde el dashboard, con aviso FCM | Se acepta | Aceptado | ✅ |
| 244 | Avisos FCM con intentos negativos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 14. Alertas técnicas del servidor

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 245 | Bloqueo de la nota de ALERTA: pg_advisory_xact_lock(hashtextextended(id_asignacion::text \|\| ':' \|\| tipo_evento, 0)) | Se acepta | Aceptado | ✅ |
| 246 | Silencio de datos del reloj de Pedro | Se acepta | Aceptado | ✅ |
| 247 | Hay un silencio de datos abierto: el job no crea otro | = 1 | = 1 | ✅ |
| 248 | Vuelven los datos: el sistema la cierra solo (sin profesional) | Se acepta | Aceptado | ✅ |
| 249 | Estado de la técnica cerrada por el sistema | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 250 | Ya no hay silencio abierto | = 0 | = 0 | ✅ |
| 251 | Alguien intenta asumir la técnica ya cerrada con la receta de la nota: no afecta filas | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 252 | Después puede abrirse un nuevo silencio de datos | Se acepta | Aceptado | ✅ |
| 253 | El reloj envía otra alerta del mismo tipo con una abierta (nunca se descarta) | Se acepta | Aceptado | ✅ |
| 254 | Clasificar como POSITIVA una alerta técnica | Lo valida el backend | Aceptado por la base | ℹ️ |
| 255 | Alerta del servidor que dice traer configuración del reloj | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 256 | Alerta del reloj sin el hash de su configuración | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 257 | El servidor crea una alerta clínica (CAIDA) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 258 | El servidor crea una alerta de batería baja (técnica, se deshace) | Se acepta | Aceptado | ✅ |
| 259 | El reloj informa un silencio de datos (solo lo puede detectar el servidor) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 15. Reintentos y mensajes duplicados (idempotencia)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 260 | El reloj reenvía la misma alerta (mismo event_id) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 261 | El backend ignora la alerta reenviada (ON CONFLICT (event_id) DO NOTHING) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 262 | El reloj repite una solicitud de aviso (no se envía dos veces) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 263 | Meta repite un webhook (mismo id de mensaje) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 264 | Aviso FCM repetido del mismo tipo para la misma alerta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 265 | Dos solicitudes distintas para el mismo contacto, canal y ronda (el reloj se reinició) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 266 | El mismo contacto en la ronda 2 (se deshace) | Se acepta | Aceptado | ✅ |

### 16. Reglas de los avisos a contactos

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 267 | Respuesta del contacto sin su hora | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 268 | Motivo de falla sin fecha de falla | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 269 | Lectura informada en un correo (solo WhatsApp) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 270 | Respuesta que no existe en los botones | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 271 | Canal que no existe (SMS) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 272 | Aviso a un vínculo que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 273 | Aviso a un vínculo de otro paciente (la hija de María en una alerta de Rosa) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 274 | Aviso por WhatsApp a una persona que solo tiene correo (el vecino) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 275 | Aviso por WhatsApp a una persona que revocó el opt-in (la cuidadora de Pedro) | Lo valida el backend | Aceptado por la base | ℹ️ |

### 17. Datos calculados (reportes, hallazgos, panel)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 276 | Reporte diario de María (hora de Chile): FC promedio / mínima / máxima | = 80 / 70 / 90 | = 80 / 70 / 90 | ✅ |
| 277 | Uso del reloj de María (minutos puesto en el día) | = 110 | = 110 | ✅ |
| 278 | Hallazgo de la red de seguridad: SpO2 bajo 90 sin alerta cercana del reloj | = 1 | = 1 | ✅ |
| 279 | Métrica de validación: SpO2 descartados por los relojes | = 5 | = 5 | ✅ |
| 280 | Métricas: alertas reales (sin simulacros ni canceladas) | = 7 | = 7 | ✅ |
| 281 | Panel: alertas reales abiertas | = 4 | = 4 | ✅ |
| 282 | Panel: alertas sin respuesta (prioridad máxima) | = 1 | = 1 | ✅ |
| 283 | Panel: casos en seguimiento | = 0 | = 0 | ✅ |
| 284 | Inventario: relojes vigentes con batería bajo 20 % en su último lote | = 1 | = 1 | ✅ |

### 18. Auditoría de cambios

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 285 | Ana modifica a María y cambia su configuración; queda en la auditoría | Se acepta | Aceptado | ✅ |
| 286 | Auditar la desactivación de un contacto, la ficha, una condición y un permiso, cada uno con su propio id | Se acepta | Aceptado | ✅ |
| 287 | Historial de un objeto con clave compuesta (configuración de María del 10-10) | = 1 | = 1 | ✅ |
| 288 | Borrar a una profesional con historial (se desactiva, no se borra) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |

### 19. Borrado al terminar el piloto (4.4.5)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 289 | Borrar un reloj asignado | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 290 | Borrar un vínculo que tiene avisos enviados | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 291 | Borrar una persona que es contacto de pacientes | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 292 | Borrar a María con todos sus datos | Se acepta | Aceptado | ✅ |
| 293 | No queda ningún dato de María | = 0 | = 0 | ✅ |
| 294 | Pedro y Rosa conservan sus datos (5 vínculos + 8 alertas) | = 13 | = 13 | ✅ |
| 295 | La auditoría sobre María sobrevive al borrado | = 2 | = 2 | ✅ |
| 296 | Seguridad Ciudadana se conserva (sigue siendo contacto de Pedro y Rosa) | = 1 | = 1 | ✅ |
| 297 | Limpieza: el backend borra las personas que quedaron sin pacientes | Se acepta | Aceptado | ✅ |
| 298 | Se borró solo la hija de María | = 3 / 0 | = 3 / 0 | ✅ |
| 299 | Se conservan profesionales, relojes y documentos de otros pacientes | = 4 / 5 | = 4 / 5 | ✅ |
