# Pruebas del modelo de datos

Informe generado automáticamente por [`pruebas-modelo-datos/prueba-modelo.mjs`](pruebas-modelo-datos/prueba-modelo.mjs). No editar a mano: se reescribe en cada ejecución.

| | |
|---|---|
| Fecha | 21 de octubre de 2026 a las 12:15 a. m. |
| Modelo | [`modelo-datos.dbml`](modelo-datos.dbml) + [`reglas-extra.sql`](pruebas-modelo-datos/reglas-extra.sql) |
| Base de datos | PostgreSQL embebido (PGlite 0.2.17), conversión con @dbml/core 3.14.1, Node v26.11.1 |
| **Resultado** | **285 casos: 272 ✅ · 0 ❌ · 0 ⚠️ · 13 ℹ️** |

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
| 2. Roles, permisos y profesionales | 27 | 25 | 0 | 0 | 2 |
| 3. Inicio de sesión calculado desde la auditoría | 8 | 8 | 0 | 0 | 0 |
| 4. Pacientes y datos de salud | 19 | 19 | 0 | 0 | 0 |
| 5. Contactos (compartidos, exclusivos y horarios) | 23 | 23 | 0 | 0 | 0 |
| 6. Configuración clínica y umbrales (historiales separados) | 23 | 23 | 0 | 0 | 0 |
| 7. Relojes y asignaciones | 23 | 20 | 0 | 0 | 3 |
| 8. Datos recibidos del reloj (lotes, lecturas, GPS) | 19 | 19 | 0 | 0 | 0 |
| 9. Configuración pendiente en el reloj (por hash) | 5 | 5 | 0 | 0 | 0 |
| 10. Flujo completo de una alerta (caída de María) | 36 | 34 | 0 | 0 | 2 |
| 11. Reglas de la atención (ATENCION_ALERTA) | 13 | 13 | 0 | 0 | 0 |
| 12. Gravedad y horario (leve, media y grave de madrugada) | 15 | 15 | 0 | 0 | 0 |
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
| 7 | Cantidad de tablas | = 28 | = 28 | ✅ |
| 8 | Tablas sueltas (sin ninguna relación) | = 0 | = 0 | ✅ |
| 9 | Cantidad de relaciones (claves foráneas) | = 31 | = 31 | ✅ |
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
| 32 | Profesional sin RUN | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 33 | Enlace con un tipo que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 34 | Enlace repetido | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 35 | Token de sesión que vence antes de emitirse | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 36 | Enlace que vence al mismo tiempo que se emite | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 37 | Sesión y códigos de respaldo de Ana | Se acepta | Aceptado | ✅ |
| 38 | Código de respaldo repetido para la misma profesional | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 39 | Token de sesión repetido | Se rechaza (clave única) | Rechazado (clave única) | ✅ |

### 3. Inicio de sesión calculado desde la auditoría

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 40 | Tres intentos fallidos de Ana en 10 minutos | Se acepta | Aceptado | ✅ |
| 41 | Ana está bloqueada (3 fallidos en 15 min, valores del yml) | = true | = true | ✅ |
| 42 | El administrador la desbloquea y Ana entra | Se acepta | Aceptado | ✅ |
| 43 | Ya no está bloqueada | = false | = false | ✅ |
| 44 | Último acceso de Ana (hace 1 minuto) | = true | = true | ✅ |
| 45 | Intento fallido con un RUN que no existe (sin usuario) | Se acepta | Aceptado | ✅ |
| 46 | Auditoría con entidad pero sin su id | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 47 | Acción de auditoría que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |

### 4. Pacientes y datos de salud

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 48 | Alta de tres pacientes con consentimiento firmado | Se acepta | Aceptado | ✅ |
| 49 | Pacientes por evaluar (dependencia vacía) | = 1 | = 1 | ✅ |
| 50 | La fecha de nacimiento (cifrada) se guarda como texto; la edad la calcula el backend | = character varying | = character varying | ✅ |
| 51 | Ficha de María (uno a uno), dos enfermedades y dos situaciones | Se acepta | Aceptado | ✅ |
| 52 | Ficha de María: enfermedades / situaciones | = 2 / 2 | = 2 / 2 | ✅ |
| 53 | Segunda ficha para el mismo paciente | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 54 | Ficha de un paciente que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 55 | Condición sin descripción | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 56 | Condición sin indicar si es enfermedad o situación | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 57 | Tipo de condición que no existe (ALERGIA) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 58 | Ana sube el consentimiento de María | Se acepta | Aceptado | ✅ |
| 59 | Dos documentos en la misma ruta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 60 | Documento subido por un usuario que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 61 | Tipo de documento que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 62 | Paciente con RUN repetido | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 63 | Paciente sin fecha de ingreso | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 64 | Baja anterior al ingreso | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 65 | Sexo que no existe en el catálogo | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 66 | Paciente con rut_hash pero sin el RUN cifrado | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |

### 5. Contactos (compartidos, exclusivos y horarios)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 67 | Catálogo de tipos de contacto | Se acepta | Aceptado | ✅ |
| 68 | Cuatro personas de contacto (una solo con correo) | Se acepta | Aceptado | ✅ |
| 69 | Listas de contactos: Seguridad Ciudadana en los tres pacientes; la cuidadora de Pedro de noche (22:00–07:00) | Se acepta | Aceptado | ✅ |
| 70 | Seguridad Ciudadana está guardada una sola vez | = 1 | = 1 | ✅ |
| 71 | Seguridad Ciudadana es contacto de tres pacientes (compartido) | = 3 | = 3 | ✅ |
| 72 | La cuidadora de Pedro está disponible a las 03:00 (horario que cruza la medianoche) | = true | = true | ✅ |
| 73 | La cuidadora de Pedro no está disponible a las 15:00 | = false | = false | ✅ |
| 74 | Meta informa la baja del opt-in de un número: se encuentra a la persona por su hash | 1 fila(s) afectada(s) | 1 fila(s) afectada(s) | ✅ |
| 75 | Misma persona registrada dos veces (mismo WhatsApp) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 76 | Misma persona registrada dos veces (mismo correo) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 77 | Persona sin WhatsApp ni correo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 78 | WhatsApp sin su hash | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 79 | Correo sin su hash | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 80 | Baja del opt-in sin opt-in previo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 81 | La misma persona dos veces en la lista de un paciente | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 82 | Dos contactos activos con la misma prioridad | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 83 | Repetir prioridad con un contacto inactivo (se deshace) | Se acepta | Aceptado | ✅ |
| 84 | Dos cuidadores principales activos para el mismo paciente | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 85 | Prioridad 0 | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 86 | Horario con inicio pero sin fin | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 87 | Horario que empieza y termina a la misma hora | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 88 | Parentesco en un contacto que no es FAMILIA | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 89 | Tipo de contacto que no existe (AMBULANCIA) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |

### 6. Configuración clínica y umbrales (historiales separados)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 90 | María: configuración inicial y un cambio posterior (activa el GPS) | Se acepta | Aceptado | ✅ |
| 91 | Pedro con valores por defecto; Rosa en modo conservador | Se acepta | Aceptado | ✅ |
| 92 | Módulos por defecto (caídas, FC, SpO2, inactividad sí; tendencia y GPS no) | = true,true,true,true,false,false | = true,true,true,true,false,false | ✅ |
| 93 | Configuración vigente de María (la más reciente): GPS activo | = true | = true | ✅ |
| 94 | Umbrales de María: FC (dos versiones) y SpO2 sin límite alto | Se acepta | Aceptado | ✅ |
| 95 | Historiales independientes: 2 configuraciones y 3 umbrales de María | = 2 / 3 | = 2 / 3 | ✅ |
| 96 | Umbral FC vigente de María: normal desde 55 | = 55 | = 55 | ✅ |
| 97 | Umbral FC que regía el 05-10 (versión del 01-10): normal desde 50 | = 50 | = 50 | ✅ |
| 98 | María vuelve al umbral base de SpO2 sin borrar historial (fila con las cuatro columnas vacías) | Se acepta | Aceptado | ✅ |
| 99 | Para SpO2 rige el umbral base | = true | = true | ✅ |
| 100 | Pedro no tiene umbrales propios (rige el base) | = 0 | = 0 | ✅ |
| 101 | Dos configuraciones con la misma fecha | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 102 | Minutos de espera en cero | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 103 | Dos umbrales de la misma variable con la misma fecha | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 104 | Normal mínimo mayor que el normal máximo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 105 | Grave bajo por encima del normal mínimo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 106 | Grave alto por debajo del normal máximo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 107 | Umbral de una variable que no existe (presión arterial) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 108 | Umbral de FC a medias (solo grave bajo y grave alto) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 109 | Umbral de FC sin límite alto | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 110 | Umbral de SpO2 con límite alto | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 111 | Umbral de SpO2 con normal desde 101 % | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 112 | Umbral de FC completo y coherente para Pedro (se deshace) | Se acepta | Aceptado | ✅ |

### 7. Relojes y asignaciones

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 113 | Registro de cinco relojes con su SIM (uno retirado) | Se acepta | Aceptado | ✅ |
| 114 | Dos relojes con la misma SIM (ICCID) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 115 | Dos relojes con el mismo identificador | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 116 | Reloj sin estado | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 117 | Relojes con el plan vencido al 20-10-2026 | = 0 | = 0 | ✅ |
| 118 | Asignación de un reloj a cada paciente | Se acepta | Aceptado | ✅ |
| 119 | Lote del reloj antiguo de Rosa | Se acepta | Aceptado | ✅ |
| 120 | Reemplazo del reloj de Rosa: se cierra la asignación y se abre otra | Se acepta | Aceptado | ✅ |
| 121 | Rosa conserva el historial: 2 asignaciones, 1 vigente | = 2/1 | = 2/1 | ✅ |
| 122 | El token del reloj antiguo ya no vale | = 0 | = 0 | ✅ |
| 123 | El lote antiguo sigue ligado a Rosa | = true | = true | ✅ |
| 124 | Relojes libres para asignar (operativos y sin asignación vigente) | = 1 | = 1 | ✅ |
| 125 | Un reloj asignado a dos pacientes a la vez | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 126 | Un paciente con dos relojes vigentes | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 127 | Reusar el token de una asignación cerrada | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 128 | Asignación sin token | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 129 | Asignación que termina antes de empezar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 130 | Una asignación cerrada del reloj antiguo de Rosa que se superpone con su historial (a otro paciente) | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 131 | Una asignación cerrada de Rosa que se superpone con sus relojes | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 132 | El reloj antiguo de Rosa se asigna a otro paciente desde el momento en que se cerró (se deshace) | Se acepta | Aceptado | ✅ |
| 133 | Asignar un reloj RETIRADO | Lo valida el backend | Aceptado por la base | ℹ️ |
| 134 | Asignar un reloj a un paciente sin consentimiento firmado | Lo valida el backend | Aceptado por la base | ℹ️ |
| 135 | Dar de baja a un paciente que tiene un reloj vigente | Lo valida el backend | Aceptado por la base | ℹ️ |

### 8. Datos recibidos del reloj (lotes, lecturas, GPS)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 136 | Lotes de cada reloj (LTE y Wi-Fi) | Se acepta | Aceptado | ✅ |
| 137 | Lecturas de FC y SpO2 con su hora de medición | Se acepta | Aceptado | ✅ |
| 138 | Lectura con una asignación distinta a la de su lote (redundancia controlada) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 139 | Segundo lote de María, que reenvía historial | Se acepta | Aceptado | ✅ |
| 140 | Lectura reenviada en otro lote | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 141 | El backend ignora la lectura reenviada (ON CONFLICT DO NOTHING) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 142 | Lectura de un tipo que no existe (presión arterial) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 143 | Lectura de un lote que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 144 | Ubicación GPS de María | Se acepta | Aceptado | ✅ |
| 145 | Ubicación repetida para la misma hora | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 146 | Ubicación con una asignación distinta a la de su lote | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 147 | Eventos descartados por el reloj, por tipo | Se acepta | Aceptado | ✅ |
| 148 | Contador de descartes en cero | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 149 | Contador de descartes repetido para el mismo tipo | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 150 | El reloj informa descartes de un evento que solo crea el servidor (SILENCIO_DATOS) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 151 | Lote repetido (mismo event_id, el reloj reintentó) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 152 | Batería de 150 % | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 153 | Minutos puesto negativos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 154 | Estado actual del reloj de María (último lote): batería | = 70 | = 70 | ✅ |

### 9. Configuración pendiente en el reloj (por hash)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 155 | Pedro: el reloj tiene h1 y el backend calcula h1 | = false | = false | ✅ |
| 156 | María cambió su configuración: el backend calcula h2 y el reloj sigue en h1 | = true | = true | ✅ |
| 157 | El reloj de María aplica la configuración nueva y la informa en su siguiente lote | Se acepta | Aceptado | ✅ |
| 158 | María al día | = false | = false | ✅ |
| 159 | Un reloj que aún no envía lotes queda pendiente | = true | = true | ✅ |

### 10. Flujo completo de una alerta (caída de María)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 160 | Llega una caída MEDIA del reloj, con su evidencia | Se acepta | Aceptado | ✅ |
| 161 | Estado inicial | = RECIBIDA / SIN_ASIGNAR | = RECIBIDA / SIN_ASIGNAR | ✅ |
| 162 | Se asigna un folio automáticamente | = true | = true | ✅ |
| 163 | Retraso por cobertura (recepción menos detección) se calcula | = true | = true | ✅ |
| 164 | El reloj pide avisar a la hija (prioridad 1) | Se acepta | Aceptado | ✅ |
| 165 | Estado al enviar el primer aviso | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 166 | Meta informa entrega y lectura (webhook) | Se acepta | Aceptado | ✅ |
| 167 | Estado de entrega calculado desde las fechas | = LEIDO | = LEIDO | ✅ |
| 168 | La hija responde "No puedo" | Se acepta | Aceptado | ✅ |
| 169 | Sigue avisando | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 170 | La caída se agrava por inmovilidad | Se acepta | Aceptado | ✅ |
| 171 | Conserva la gravedad con que llegó | = MEDIA | = MEDIA | ✅ |
| 172 | Gravedad actual calculada | = GRAVE | = GRAVE | ✅ |
| 173 | El reloj pasa a Seguridad Ciudadana (prioridad 2), que responde "Voy en camino" | Se acepta | Aceptado | ✅ |
| 174 | Estado tras la confirmación | = CONFIRMADA / SIN_ASIGNAR | = CONFIRMADA / SIN_ASIGNAR | ✅ |
| 175 | Aviso FCM al reloj y el reloj informa que lo recibió | Se acepta | Aceptado | ✅ |
| 176 | Contacto que confirmó (para la pantalla del reloj) | = Seguridad Ciudadana | = Seguridad Ciudadana | ✅ |
| 177 | Ana asume el caso | 1 fila(s) afectada(s) | 1 fila(s) afectada(s) | ✅ |
| 178 | Estado al asumir | = CONFIRMADA / EN_ATENCION | = CONFIRMADA / EN_ATENCION | ✅ |
| 179 | Luis intenta asumirlo un momento después: no afecta filas (ya lo tomó Ana) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 180 | La responsable sigue siendo Ana | = Ana | = Ana | ✅ |
| 181 | Ana la resuelve como POSITIVA y queda a cargo del seguimiento | Se acepta | Aceptado | ✅ |
| 182 | Estado tras el cierre | = RESUELTA / EN_SEGUIMIENTO | = RESUELTA / EN_SEGUIMIENTO | ✅ |
| 183 | Reasignación del seguimiento a Luis | Se acepta | Aceptado | ✅ |
| 184 | Catálogo de destinos de derivación | Se acepta | Aceptado | ✅ |
| 185 | Llamada y derivación al CESFAM | Se acepta | Aceptado | ✅ |
| 186 | "Derivada" se ve en el seguimiento (no es un estado) | = 1 | = 1 | ✅ |
| 187 | Derivación sin destino | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 188 | Llamada con destino de derivación | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 189 | Derivación a un destino que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 190 | Cierre del seguimiento antes del cierre de la alerta | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 191 | Cierre del seguimiento | Se acepta | Aceptado | ✅ |
| 192 | Estado final | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 193 | Agregar un seguimiento después del cierre del seguimiento | Lo valida el backend | Aceptado por la base | ℹ️ |
| 194 | Historial del caso (avisos + FCM + seguimientos) | = 5 | = 5 | ✅ |
| 195 | El equipo cambia la gravedad con que llegó la alerta | Lo valida el backend | Aceptado por la base | ℹ️ |

### 11. Reglas de la atención (ATENCION_ALERTA)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 196 | Media de madrugada (Rosa): avisa de inmediato a Seguridad Ciudadana (compartido) | Se acepta | Aceptado | ✅ |
| 197 | Atención vacía (sin toma ni cierre) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 198 | Toma sin responsable | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 199 | Responsable sin hora de toma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 200 | Clasificar sin cerrar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 201 | Observaciones de cierre sin cerrar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 202 | Cierre anterior a la toma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 203 | POSITIVA sin responsable del seguimiento | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 204 | Cierre de seguimiento en una falsa alarma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 205 | Responsable que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 206 | Clasificación que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 207 | Dos atenciones para la misma alerta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 208 | Atención de una alerta que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |

### 12. Gravedad y horario (leve, media y grave de madrugada)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 209 | Leve de madrugada (Pedro): el reloj no avisa y espera el día hábil | Se acepta | Aceptado | ✅ |
| 210 | Queda en la bandeja de día hábil | = PENDIENTE_DIA_HABIL / SIN_ASIGNAR | = PENDIENTE_DIA_HABIL / SIN_ASIGNAR | ✅ |
| 211 | El folio crece con cada alerta | = true | = true | ✅ |
| 212 | A primera hora, Ana la cierra directamente como falsa alarma (sin asumirla antes) | Se acepta | Aceptado | ✅ |
| 213 | Estado tras la revisión | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 214 | La media está avisando | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 215 | Grave de madrugada (Rosa): el reloj recorre la lista dos veces | Se acepta | Aceptado | ✅ |
| 216 | Nadie confirma: el reloj informa el fin de la lista | Se acepta | Aceptado | ✅ |
| 217 | Estado de la grave sin respuesta | = SIN_RESPUESTA / SIN_ASIGNAR | = SIN_RESPUESTA / SIN_ASIGNAR | ✅ |
| 218 | Una media marcada para esperar el día hábil | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 219 | Una grave marcada para esperar el día hábil | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 220 | Gravedad que no existe (MODERADA) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 221 | Agravar una alerta que llegó grave | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 222 | Agravar una alerta que no es caída | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 223 | Tercera ronda de avisos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 13. Cancelación por el paciente y simulacro

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 224 | El paciente cancela una caída en los 30 segundos | Se acepta | Aceptado | ✅ |
| 225 | Estado de la cancelada | = CANCELADA / SIN_ASIGNAR | = CANCELADA / SIN_ASIGNAR | ✅ |
| 226 | Una cancelada no puede quedar sin respuesta | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 227 | Una cancelada recibe atención del equipo | Lo valida el backend | Aceptado por la base | ℹ️ |
| 228 | Una cancelada tiene avisos a contactos | Lo valida el backend | Aceptado por la base | ℹ️ |
| 229 | Simulacro desde el dashboard, con aviso FCM | Se acepta | Aceptado | ✅ |
| 230 | Avisos FCM con intentos negativos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 14. Alertas técnicas del servidor

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 231 | Bloqueo de la nota de ALERTA: pg_advisory_xact_lock(hashtextextended(id_asignacion::text \|\| ':' \|\| tipo_evento, 0)) | Se acepta | Aceptado | ✅ |
| 232 | Silencio de datos del reloj de Pedro | Se acepta | Aceptado | ✅ |
| 233 | Hay un silencio de datos abierto: el job no crea otro | = 1 | = 1 | ✅ |
| 234 | Vuelven los datos: el sistema la cierra solo (sin profesional) | Se acepta | Aceptado | ✅ |
| 235 | Estado de la técnica cerrada por el sistema | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 236 | Ya no hay silencio abierto | = 0 | = 0 | ✅ |
| 237 | Alguien intenta asumir la técnica ya cerrada con la receta de la nota: no afecta filas | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 238 | Después puede abrirse un nuevo silencio de datos | Se acepta | Aceptado | ✅ |
| 239 | El reloj envía otra alerta del mismo tipo con una abierta (nunca se descarta) | Se acepta | Aceptado | ✅ |
| 240 | Clasificar como POSITIVA una alerta técnica | Lo valida el backend | Aceptado por la base | ℹ️ |
| 241 | Alerta del servidor que dice traer configuración del reloj | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 242 | Alerta del reloj sin el hash de su configuración | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 243 | El servidor crea una alerta clínica (CAIDA) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 244 | El servidor crea una alerta de batería baja (técnica, se deshace) | Se acepta | Aceptado | ✅ |
| 245 | El reloj informa un silencio de datos (solo lo puede detectar el servidor) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 15. Reintentos y mensajes duplicados (idempotencia)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 246 | El reloj reenvía la misma alerta (mismo event_id) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 247 | El backend ignora la alerta reenviada (ON CONFLICT (event_id) DO NOTHING) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 248 | El reloj repite una solicitud de aviso (no se envía dos veces) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 249 | Meta repite un webhook (mismo id de mensaje) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 250 | Aviso FCM repetido del mismo tipo para la misma alerta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 251 | Dos solicitudes distintas para el mismo contacto, canal y ronda (el reloj se reinició) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 252 | El mismo contacto en la ronda 2 (se deshace) | Se acepta | Aceptado | ✅ |

### 16. Reglas de los avisos a contactos

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 253 | Respuesta del contacto sin su hora | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 254 | Motivo de falla sin fecha de falla | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 255 | Lectura informada en un correo (solo WhatsApp) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 256 | Respuesta que no existe en los botones | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 257 | Canal que no existe (SMS) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 258 | Aviso a un vínculo que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 259 | Aviso a un vínculo de otro paciente (la hija de María en una alerta de Rosa) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 260 | Aviso por WhatsApp a una persona que solo tiene correo (el vecino) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 261 | Aviso por WhatsApp a una persona que revocó el opt-in (la cuidadora de Pedro) | Lo valida el backend | Aceptado por la base | ℹ️ |

### 17. Datos calculados (reportes, hallazgos, panel)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 262 | Reporte diario de María (hora de Chile): FC promedio / mínima / máxima | = 80 / 70 / 90 | = 80 / 70 / 90 | ✅ |
| 263 | Uso del reloj de María (minutos puesto en el día) | = 110 | = 110 | ✅ |
| 264 | Hallazgo de la red de seguridad: SpO2 bajo 90 sin alerta cercana del reloj | = 1 | = 1 | ✅ |
| 265 | Métrica de validación: SpO2 descartados por los relojes | = 5 | = 5 | ✅ |
| 266 | Métricas: alertas reales (sin simulacros ni canceladas) | = 7 | = 7 | ✅ |
| 267 | Panel: alertas reales abiertas | = 4 | = 4 | ✅ |
| 268 | Panel: alertas sin respuesta (prioridad máxima) | = 1 | = 1 | ✅ |
| 269 | Panel: casos en seguimiento | = 0 | = 0 | ✅ |
| 270 | Inventario: relojes vigentes con batería bajo 20 % en su último lote | = 1 | = 1 | ✅ |

### 18. Auditoría de cambios

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 271 | Ana modifica a María y cambia su configuración; queda en la auditoría | Se acepta | Aceptado | ✅ |
| 272 | Auditar la desactivación de un contacto, la ficha, una condición y un permiso, cada uno con su propio id | Se acepta | Aceptado | ✅ |
| 273 | Historial de un objeto con clave compuesta (configuración de María del 10-10) | = 1 | = 1 | ✅ |
| 274 | Borrar a una profesional con historial (se desactiva, no se borra) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |

### 19. Borrado al terminar el piloto (4.4.5)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 275 | Borrar un reloj asignado | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 276 | Borrar un vínculo que tiene avisos enviados | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 277 | Borrar una persona que es contacto de pacientes | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 278 | Borrar a María con todos sus datos | Se acepta | Aceptado | ✅ |
| 279 | No queda ningún dato de María | = 0 | = 0 | ✅ |
| 280 | Pedro y Rosa conservan sus datos (5 vínculos + 8 alertas) | = 13 | = 13 | ✅ |
| 281 | La auditoría sobre María sobrevive al borrado | = 2 | = 2 | ✅ |
| 282 | Seguridad Ciudadana se conserva (sigue siendo contacto de Pedro y Rosa) | = 1 | = 1 | ✅ |
| 283 | Limpieza: el backend borra las personas que quedaron sin pacientes | Se acepta | Aceptado | ✅ |
| 284 | Se borró solo la hija de María | = 3 / 0 | = 3 / 0 | ✅ |
| 285 | Se conservan profesionales, relojes y documentos de otros pacientes | = 4 / 5 | = 4 / 5 | ✅ |
