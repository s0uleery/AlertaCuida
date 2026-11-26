# Pruebas del modelo de datos

Informe generado automáticamente por [`pruebas-modelo-datos/prueba-modelo.mjs`](pruebas-modelo-datos/prueba-modelo.mjs). No editar a mano: se reescribe en cada ejecución.

| | |
|---|---|
| Fecha | 26 de noviembre de 2026 a las 7:41 p. m. |
| Modelo | [`modelo-datos.dbml`](modelo-datos.dbml) + [`reglas-extra.sql`](pruebas-modelo-datos/reglas-extra.sql) |
| Base de datos | PostgreSQL embebido (PGlite 0.2.17), conversión con @dbml/core 3.14.1, Node v26.11.1 |
| **Resultado** | **340 casos: 326 ✅ · 0 ❌ · 0 ⚠️ · 14 ℹ️** |

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
| 5. Contactos (compartidos, exclusivos, horarios y orden por gravedad) | 25 | 25 | 0 | 0 | 0 |
| 6. Configuración clínica y umbrales (historiales separados) | 30 | 30 | 0 | 0 | 0 |
| 7. Relojes y asignaciones | 23 | 20 | 0 | 0 | 3 |
| 8. Datos recibidos del reloj (lotes, lecturas, GPS) | 19 | 19 | 0 | 0 | 0 |
| 9. Configuración pendiente en el reloj (por hash) | 5 | 5 | 0 | 0 | 0 |
| 10. Flujo completo de una alerta (caída de María) | 42 | 40 | 0 | 0 | 2 |
| 11. Reglas de la atención (ATENCION_ALERTA) | 13 | 13 | 0 | 0 | 0 |
| 12. Gravedad y horario (leve, media y grave de madrugada) | 18 | 18 | 0 | 0 | 0 |
| 13. Cancelación por el paciente y simulacro | 7 | 5 | 0 | 0 | 2 |
| 14. Alertas técnicas del servidor | 18 | 17 | 0 | 0 | 1 |
| 15. Reintentos y mensajes duplicados (idempotencia) | 7 | 7 | 0 | 0 | 0 |
| 16. Reglas de los avisos a contactos | 38 | 35 | 0 | 0 | 3 |
| 17. Datos calculados (reportes, hallazgos, panel) | 9 | 9 | 0 | 0 | 0 |
| 18. Auditoría de cambios | 4 | 4 | 0 | 0 | 0 |
| 19. Borrado al terminar el piloto (4.4.5) | 12 | 12 | 0 | 0 | 0 |

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
| 7 | Cantidad de tablas | = 33 | = 33 | ✅ |
| 8 | Tablas sueltas (sin ninguna relación), salvo los códigos de emparejamiento y el horario hábil | = 0 | = 0 | ✅ |
| 9 | Cantidad de relaciones (claves foráneas) | = 36 | = 36 | ✅ |
| 10 | Cantidad de tipos (Enum) | = 22 | = 22 | ✅ |
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

### 5. Contactos (compartidos, exclusivos, horarios y orden por gravedad)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 71 | Catálogo de tipos de contacto | Se acepta | Aceptado | ✅ |
| 72 | Cuatro personas de contacto (una solo con correo) | Se acepta | Aceptado | ✅ |
| 73 | Listas de contactos: Seguridad Ciudadana en los tres pacientes; la cuidadora de Pedro de noche (22:00–07:00) | Se acepta | Aceptado | ✅ |
| 74 | Cada vínculo con su lugar en las tres gravedades; en grave, Seguridad Ciudadana va primero con María | Se acepta | Aceptado | ✅ |
| 75 | La lista de grave de María empieza por Seguridad Ciudadana | = SEGURIDAD_CIUDADANA | = SEGURIDAD_CIUDADANA | ✅ |
| 76 | Seguridad Ciudadana está guardada una sola vez | = 1 | = 1 | ✅ |
| 77 | Seguridad Ciudadana es contacto de tres pacientes (compartido) | = 3 | = 3 | ✅ |
| 78 | La cuidadora de Pedro está disponible a las 03:00 (horario que cruza la medianoche) | = true | = true | ✅ |
| 79 | La cuidadora de Pedro no está disponible a las 15:00 | = false | = false | ✅ |
| 80 | Meta informa la baja del opt-in de un número: se encuentra a la persona por su hash | 1 fila(s) afectada(s) | 1 fila(s) afectada(s) | ✅ |
| 81 | Misma persona registrada dos veces (mismo WhatsApp) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 82 | Misma persona registrada dos veces (mismo correo) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 83 | Persona sin WhatsApp ni correo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 84 | WhatsApp sin su hash | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 85 | Correo sin su hash | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 86 | Baja del opt-in sin opt-in previo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 87 | La misma persona dos veces en la lista de un paciente | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 88 | Dos cuidadores principales activos para el mismo paciente | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 89 | Horario con inicio pero sin fin | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 90 | Horario que empieza y termina a la misma hora | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 91 | Parentesco en un contacto que no es tercero significativo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 92 | Tipo de contacto que no existe (AMBULANCIA) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 93 | Un vínculo dos veces en la misma gravedad | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 94 | Orden 0 en una gravedad | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 95 | Gravedad que no existe (URGENTE) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |

### 6. Configuración clínica y umbrales (historiales separados)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 96 | María: configuración inicial y un cambio posterior (activa el GPS) | Se acepta | Aceptado | ✅ |
| 97 | Pedro con valores por defecto; Rosa en modo conservador | Se acepta | Aceptado | ✅ |
| 98 | Módulos por defecto (caídas, FC, SpO2, inactividad, tendencia y botón sí; GPS y zona no) | = true,true,true,true,true,false,false,true | = true,true,true,true,true,false,false,true | ✅ |
| 99 | Zona segura activa sin GPS | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 100 | Zona segura de Pedro: 200 m, media por defecto, coordenadas cifradas como texto | Se acepta | Aceptado | ✅ |
| 101 | La zona sin severidad elegida es media | = MEDIA | = MEDIA | ✅ |
| 102 | Pedro activa la zona con el GPS (se deshace) | Se acepta | Aceptado | ✅ |
| 103 | Zona segura con radio cero | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 104 | Zona segura con una severidad que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 105 | Dos zonas de Pedro con la misma fecha | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 106 | Configuración vigente de María (la más reciente): GPS activo | = true | = true | ✅ |
| 107 | Umbrales de María: FC (dos versiones) y SpO2 sin límite alto | Se acepta | Aceptado | ✅ |
| 108 | Historiales independientes: 2 configuraciones y 3 umbrales de María | = 2 / 3 | = 2 / 3 | ✅ |
| 109 | Umbral FC vigente de María: normal desde 55 | = 55 | = 55 | ✅ |
| 110 | Umbral FC que regía el 05-10 (versión del 01-10): normal desde 50 | = 50 | = 50 | ✅ |
| 111 | María vuelve al umbral base de SpO2 sin borrar historial (fila con las cuatro columnas vacías) | Se acepta | Aceptado | ✅ |
| 112 | Para SpO2 rige el umbral base | = true | = true | ✅ |
| 113 | Pedro no tiene umbrales propios (rige el base) | = 0 | = 0 | ✅ |
| 114 | Dos configuraciones con la misma fecha | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 115 | Minutos de espera en cero | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 116 | Dos umbrales de la misma variable con la misma fecha | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 117 | Normal mínimo mayor que el normal máximo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 118 | Grave bajo por encima del normal mínimo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 119 | Grave alto por debajo del normal máximo | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 120 | Umbral de una variable que no existe (presión arterial) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 121 | Umbral de FC a medias (solo grave bajo y grave alto) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 122 | Umbral de FC sin límite alto | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 123 | Umbral de SpO2 con límite alto | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 124 | Umbral de SpO2 con normal desde 101 % | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 125 | Umbral de FC completo y coherente para Pedro (se deshace) | Se acepta | Aceptado | ✅ |

### 7. Relojes y asignaciones

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 126 | Registro de cinco relojes con su SIM (uno retirado) | Se acepta | Aceptado | ✅ |
| 127 | Dos relojes con la misma SIM (ICCID) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 128 | Dos relojes con el mismo identificador | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 129 | Reloj sin estado | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 130 | Relojes con el plan vencido al 20-10-2026 | = 0 | = 0 | ✅ |
| 131 | Asignación de un reloj a cada paciente | Se acepta | Aceptado | ✅ |
| 132 | Lote del reloj antiguo de Rosa | Se acepta | Aceptado | ✅ |
| 133 | Reemplazo del reloj de Rosa: se cierra la asignación y se abre otra | Se acepta | Aceptado | ✅ |
| 134 | Rosa conserva el historial: 2 asignaciones, 1 vigente | = 2/1 | = 2/1 | ✅ |
| 135 | El token del reloj antiguo ya no vale | = 0 | = 0 | ✅ |
| 136 | El lote antiguo sigue ligado a Rosa | = true | = true | ✅ |
| 137 | Relojes libres para asignar (operativos y sin asignación vigente) | = 1 | = 1 | ✅ |
| 138 | Un reloj asignado a dos pacientes a la vez | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 139 | Un paciente con dos relojes vigentes | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 140 | Reusar el token de una asignación cerrada | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 141 | Asignación sin token | Se rechaza (NOT NULL) | Rechazado (NOT NULL) | ✅ |
| 142 | Asignación que termina antes de empezar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 143 | Una asignación cerrada del reloj antiguo de Rosa que se superpone con su historial (a otro paciente) | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 144 | Una asignación cerrada de Rosa que se superpone con sus relojes | Se rechaza (superposición (EXCLUDE)) | Rechazado (superposición (EXCLUDE)) | ✅ |
| 145 | El reloj antiguo de Rosa se asigna a otro paciente desde el momento en que se cerró (se deshace) | Se acepta | Aceptado | ✅ |
| 146 | Asignar un reloj RETIRADO | Lo valida el backend | Aceptado por la base | ℹ️ |
| 147 | Asignar un reloj a un paciente sin consentimiento firmado | Lo valida el backend | Aceptado por la base | ℹ️ |
| 148 | Dar de baja a un paciente que tiene un reloj vigente | Lo valida el backend | Aceptado por la base | ℹ️ |

### 8. Datos recibidos del reloj (lotes, lecturas, GPS)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 149 | Lotes de cada reloj (LTE y Wi-Fi) | Se acepta | Aceptado | ✅ |
| 150 | Lecturas de FC y SpO2 con su hora de medición | Se acepta | Aceptado | ✅ |
| 151 | Lectura con una asignación distinta a la de su lote (redundancia controlada) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 152 | Segundo lote de María, que reenvía historial | Se acepta | Aceptado | ✅ |
| 153 | Lectura reenviada en otro lote | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 154 | El backend ignora la lectura reenviada (ON CONFLICT DO NOTHING) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 155 | Lectura de un tipo que no existe (presión arterial) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 156 | Lectura de un lote que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 157 | Ubicación GPS de María | Se acepta | Aceptado | ✅ |
| 158 | Ubicación repetida para la misma hora | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 159 | Ubicación con una asignación distinta a la de su lote | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 160 | Eventos descartados por el reloj, por tipo | Se acepta | Aceptado | ✅ |
| 161 | Contador de descartes en cero | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 162 | Contador de descartes repetido para el mismo tipo | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 163 | El reloj informa descartes de un evento que solo crea el servidor (SILENCIO_DATOS) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 164 | Lote repetido (mismo event_id, el reloj reintentó) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 165 | Batería de 150 % | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 166 | Minutos puesto negativos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 167 | Estado actual del reloj de María (último lote): batería | = 70 | = 70 | ✅ |

### 9. Configuración pendiente en el reloj (por hash)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 168 | Pedro: el reloj tiene h1 y el backend calcula h1 | = false | = false | ✅ |
| 169 | María cambió su configuración: el backend calcula h2 y el reloj sigue en h1 | = true | = true | ✅ |
| 170 | El reloj de María aplica la configuración nueva y la informa en su siguiente lote | Se acepta | Aceptado | ✅ |
| 171 | María al día | = false | = false | ✅ |
| 172 | Un reloj que aún no envía lotes queda pendiente | = true | = true | ✅ |

### 10. Flujo completo de una alerta (caída de María)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 173 | Llega una caída MEDIA del reloj, con su evidencia | Se acepta | Aceptado | ✅ |
| 174 | Estado inicial | = RECIBIDA / SIN_ASIGNAR | = RECIBIDA / SIN_ASIGNAR | ✅ |
| 175 | Se asigna un folio automáticamente | = true | = true | ✅ |
| 176 | Retraso por cobertura (recepción menos detección) se calcula | = true | = true | ✅ |
| 177 | El reloj pide avisar a la hija (prioridad 1) | Se acepta | Aceptado | ✅ |
| 178 | Estado al enviar el primer aviso | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 179 | Meta informa entrega y lectura (webhook) | Se acepta | Aceptado | ✅ |
| 180 | Estado de entrega calculado desde las fechas | = LEIDO | = LEIDO | ✅ |
| 181 | La hija responde "No puedo" | Se acepta | Aceptado | ✅ |
| 182 | Sigue avisando | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 183 | Segundos de inmovilidad sin agravamiento | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 184 | La caída se agrava por inmovilidad (90 s sin moverse) | Se acepta | Aceptado | ✅ |
| 185 | Segundos de inmovilidad negativos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 186 | La caída llegó con una discrepancia: el reloj usaba otra versión de la configuración | Se acepta | Aceptado | ✅ |
| 187 | La misma discrepancia dos veces en la alerta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 188 | Discrepancia con un código que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 189 | Discrepancia de una alerta que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 190 | Conserva la gravedad con que llegó | = MEDIA | = MEDIA | ✅ |
| 191 | Gravedad actual calculada | = GRAVE | = GRAVE | ✅ |
| 192 | El reloj pasa a Seguridad Ciudadana (prioridad 2), que responde "Voy en camino" | Se acepta | Aceptado | ✅ |
| 193 | Estado tras la confirmación | = CONFIRMADA / SIN_ASIGNAR | = CONFIRMADA / SIN_ASIGNAR | ✅ |
| 194 | Aviso FCM al reloj y el reloj informa que lo recibió | Se acepta | Aceptado | ✅ |
| 195 | Contacto que confirmó (para la pantalla del reloj) | = Seguridad Ciudadana | = Seguridad Ciudadana | ✅ |
| 196 | Ana asume el caso | 1 fila(s) afectada(s) | 1 fila(s) afectada(s) | ✅ |
| 197 | Estado al asumir | = CONFIRMADA / EN_ATENCION | = CONFIRMADA / EN_ATENCION | ✅ |
| 198 | Luis intenta asumirlo un momento después: no afecta filas (ya lo tomó Ana) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 199 | La responsable sigue siendo Ana | = Ana | = Ana | ✅ |
| 200 | Ana la resuelve como POSITIVA y queda a cargo del seguimiento | Se acepta | Aceptado | ✅ |
| 201 | Estado tras el cierre | = RESUELTA / EN_SEGUIMIENTO | = RESUELTA / EN_SEGUIMIENTO | ✅ |
| 202 | Reasignación del seguimiento a Luis | Se acepta | Aceptado | ✅ |
| 203 | Catálogo de destinos de derivación | Se acepta | Aceptado | ✅ |
| 204 | Llamada y derivación al CESFAM | Se acepta | Aceptado | ✅ |
| 205 | "Derivada" se ve en el seguimiento (no es un estado) | = 1 | = 1 | ✅ |
| 206 | Derivación sin destino | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 207 | Llamada con destino de derivación | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 208 | Derivación a un destino que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 209 | Cierre del seguimiento antes del cierre de la alerta | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 210 | Cierre del seguimiento | Se acepta | Aceptado | ✅ |
| 211 | Estado final | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 212 | Agregar un seguimiento después del cierre del seguimiento | Lo valida el backend | Aceptado por la base | ℹ️ |
| 213 | Historial del caso (avisos + FCM + seguimientos) | = 5 | = 5 | ✅ |
| 214 | El equipo cambia la gravedad con que llegó la alerta | Lo valida el backend | Aceptado por la base | ℹ️ |

### 11. Reglas de la atención (ATENCION_ALERTA)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 215 | Media de madrugada (Rosa): avisa de inmediato a Seguridad Ciudadana (compartido) | Se acepta | Aceptado | ✅ |
| 216 | Atención vacía (sin toma ni cierre) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 217 | Toma sin responsable | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 218 | Responsable sin hora de toma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 219 | Clasificar sin cerrar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 220 | Observaciones de cierre sin cerrar | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 221 | Cierre anterior a la toma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 222 | POSITIVA sin responsable del seguimiento | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 223 | Cierre de seguimiento en una falsa alarma | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 224 | Responsable que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 225 | Clasificación que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 226 | Dos atenciones para la misma alerta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 227 | Atención de una alerta que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |

### 12. Gravedad y horario (leve, media y grave de madrugada)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 228 | Leve de madrugada (Pedro): el reloj no avisa y espera el día hábil | Se acepta | Aceptado | ✅ |
| 229 | Queda en la bandeja de día hábil | = PENDIENTE_DIA_HABIL / SIN_ASIGNAR | = PENDIENTE_DIA_HABIL / SIN_ASIGNAR | ✅ |
| 230 | El folio crece con cada alerta | = true | = true | ✅ |
| 231 | A primera hora, Ana la cierra directamente como falsa alarma (sin asumirla antes) | Se acepta | Aceptado | ✅ |
| 232 | Estado tras la revisión | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 233 | La media está avisando | = AVISANDO_CONTACTO / SIN_ASIGNAR | = AVISANDO_CONTACTO / SIN_ASIGNAR | ✅ |
| 234 | Grave de madrugada (Rosa): el reloj recorre la lista dos veces | Se acepta | Aceptado | ✅ |
| 235 | Nadie confirma: el reloj informa el fin de la lista | Se acepta | Aceptado | ✅ |
| 236 | Estado de la grave sin respuesta | = SIN_RESPUESTA / SIN_ASIGNAR | = SIN_RESPUESTA / SIN_ASIGNAR | ✅ |
| 237 | Una media marcada para esperar el día hábil | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 238 | Una grave marcada para esperar el día hábil | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 239 | Gravedad que no existe (MODERADA) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 240 | Un botón de ayuda que no es leve | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 241 | Botón de ayuda de madrugada: leve, espera el día hábil (se deshace) | Se acepta | Aceptado | ✅ |
| 242 | Salida de la zona segura con la gravedad de la zona (se deshace) | Se acepta | Aceptado | ✅ |
| 243 | Agravar una alerta que llegó grave | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 244 | Agravar una alerta que no es caída | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 245 | Tercera ronda de avisos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 13. Cancelación por el paciente y simulacro

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 246 | El paciente cancela una caída en los 30 segundos | Se acepta | Aceptado | ✅ |
| 247 | Estado de la cancelada | = CANCELADA / SIN_ASIGNAR | = CANCELADA / SIN_ASIGNAR | ✅ |
| 248 | Una cancelada no puede quedar sin respuesta | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 249 | Una cancelada recibe atención del equipo | Lo valida el backend | Aceptado por la base | ℹ️ |
| 250 | Una cancelada tiene avisos a contactos | Lo valida el backend | Aceptado por la base | ℹ️ |
| 251 | Simulacro desde el dashboard, con aviso FCM | Se acepta | Aceptado | ✅ |
| 252 | Avisos FCM con intentos negativos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 14. Alertas técnicas del servidor

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 253 | Bloqueo de la nota de ALERTA: pg_advisory_xact_lock(hashtextextended(id_asignacion::text \|\| ':' \|\| tipo_evento, 0)) | Se acepta | Aceptado | ✅ |
| 254 | Silencio de datos del reloj de Pedro | Se acepta | Aceptado | ✅ |
| 255 | Hay un silencio de datos abierto: el job no crea otro | = 1 | = 1 | ✅ |
| 256 | Vuelven los datos: el sistema la cierra solo (sin profesional) | Se acepta | Aceptado | ✅ |
| 257 | Estado de la técnica cerrada por el sistema | = RESUELTA / RESUELTA | = RESUELTA / RESUELTA | ✅ |
| 258 | Ya no hay silencio abierto | = 0 | = 0 | ✅ |
| 259 | Alguien intenta asumir la técnica ya cerrada con la receta de la nota: no afecta filas | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 260 | Después puede abrirse un nuevo silencio de datos | Se acepta | Aceptado | ✅ |
| 261 | El reloj envía otra alerta del mismo tipo con una abierta (nunca se descarta) | Se acepta | Aceptado | ✅ |
| 262 | Clasificar como POSITIVA una alerta técnica | Lo valida el backend | Aceptado por la base | ℹ️ |
| 263 | Alerta del servidor que dice traer configuración del reloj | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 264 | Alerta del reloj sin el hash de su configuración | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 265 | El servidor crea una alerta clínica (CAIDA) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 266 | El servidor crea una alerta de batería baja (técnica, se deshace) | Se acepta | Aceptado | ✅ |
| 267 | El reloj informa un silencio de datos (solo lo puede detectar el servidor) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 268 | El servidor crea una alerta por exceso de alertas del reloj (técnica, se deshace) | Se acepta | Aceptado | ✅ |
| 269 | El reloj informa un exceso de alertas (solo lo puede detectar el servidor) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 270 | Una cuenta nueva no recibe avisos por correo hasta que se encienda | = false | = false | ✅ |

### 15. Reintentos y mensajes duplicados (idempotencia)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 271 | El reloj reenvía la misma alerta (mismo event_id) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 272 | El backend ignora la alerta reenviada (ON CONFLICT (event_id) DO NOTHING) | 0 fila(s) afectada(s) | 0 fila(s) afectada(s) | ✅ |
| 273 | El reloj repite una solicitud de aviso (no se envía dos veces) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 274 | Meta repite un webhook (mismo id de mensaje) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 275 | Aviso FCM repetido del mismo tipo para la misma alerta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 276 | Dos solicitudes distintas para el mismo contacto, canal y ronda (el reloj se reinició) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 277 | El mismo contacto en la ronda 2 (se deshace) | Se acepta | Aceptado | ✅ |

### 16. Reglas de los avisos a contactos

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 278 | Respuesta del contacto sin su hora | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 279 | Motivo de falla sin fecha de falla | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 280 | Lectura informada en un correo (solo WhatsApp) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 281 | Respuesta que no existe en los botones | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 282 | Canal que no existe (SMS) | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 283 | Aviso a un vínculo que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 284 | Aviso a un vínculo de otro paciente (la hija de María en una alerta de Rosa) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 285 | Aviso por WhatsApp a una persona que solo tiene correo (el vecino) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 286 | Aviso por WhatsApp a una persona que revocó el opt-in (la cuidadora de Pedro) | Lo valida el backend | Aceptado por la base | ℹ️ |
| 287 | Ana como contacto interno: solo el enlace a su cuenta, sin copiar sus datos | Se acepta | Aceptado | ✅ |
| 288 | Segundo contacto interno para la misma cuenta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 289 | Contacto interno con nombre propio (se lee de la cuenta) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 290 | Contacto interno con correo propio (se lee de la cuenta) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 291 | Contacto externo sin nombre | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 292 | Contacto interno de una cuenta que no existe | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 293 | El contacto interno en la lista de un paciente (el reloj no recorre al equipo) | Se rechaza (disparador (como un CHECK)) | Rechazado (disparador (como un CHECK)) | ✅ |
| 294 | Cambiar un vínculo de la lista para que apunte al contacto interno | Se rechaza (disparador (como un CHECK)) | Rechazado (disparador (como un CHECK)) | ✅ |
| 295 | Convertir en interno a una persona de la lista | Se rechaza (disparador (como un CHECK)) | Rechazado (disparador (como un CHECK)) | ✅ |
| 296 | Correo del servidor a Ana por un silencio de datos, pendiente de envío | Se acepta | Aceptado | ✅ |
| 297 | Un aviso al equipo no cambia el estado de contacto (no es el recorrido del reloj) | = RECIBIDA / SIN_ASIGNAR | = RECIBIDA / SIN_ASIGNAR | ✅ |
| 298 | Segundo correo a la misma cuenta por la misma alerta | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 299 | El aviso guarda cuándo se pidió (por defecto, ahora) | = 1 | = 1 | ✅ |
| 300 | Correo del vigilante del escalamiento a la misma cuenta por la misma alerta (otro propósito) | Se acepta | Aceptado | ✅ |
| 301 | Segundo correo del vigilante a la misma cuenta por la misma alerta (una vez por alerta) | Se rechaza (clave única) | Rechazado (clave única) | ✅ |
| 302 | Aviso del vigilante a un contacto del paciente (solo va al equipo) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 303 | Aviso de agravamiento a la misma cuenta por la misma alerta (otro propósito) | Se acepta | Aceptado | ✅ |
| 304 | Aviso de agravamiento a un contacto del paciente (solo va al equipo) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 305 | Propósito que no existe | Se rechaza (valor fuera del enum) | Rechazado (valor fuera del enum) | ✅ |
| 306 | Aviso interno dirigido a una persona de la lista de un paciente | Se rechaza (disparador (como un CHECK)) | Rechazado (disparador (como un CHECK)) | ✅ |
| 307 | Aviso interno con ronda (las rondas son del recorrido del reloj) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 308 | Aviso a un vínculo sin ronda | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 309 | Aviso a un vínculo y a un contacto interno a la vez | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 310 | Aviso sin destino | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 311 | Un coordinador responde el aviso a la oficina (V14: su "Voy en camino" también confirma) | Se acepta | Aceptado | ✅ |
| 312 | Respuesta en el correo del vigilante (no pide confirmar) | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 313 | Reintentos negativos | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 314 | Pendiente de envío pero ya enviado | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |
| 315 | Pendiente de envío pero ya marcado como fallido | Se rechaza (CHECK) | Rechazado (CHECK) | ✅ |

### 17. Datos calculados (reportes, hallazgos, panel)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 316 | Reporte diario de María (hora de Chile): FC promedio / mínima / máxima | = 80 / 70 / 90 | = 80 / 70 / 90 | ✅ |
| 317 | Uso del reloj de María (minutos puesto en el día) | = 110 | = 110 | ✅ |
| 318 | Hallazgo de la red de seguridad: SpO2 bajo 90 sin alerta cercana del reloj | = 1 | = 1 | ✅ |
| 319 | Métrica de validación: SpO2 descartados por los relojes | = 5 | = 5 | ✅ |
| 320 | Métricas: alertas reales (sin simulacros ni canceladas) | = 7 | = 7 | ✅ |
| 321 | Panel: alertas reales abiertas | = 4 | = 4 | ✅ |
| 322 | Panel: alertas sin respuesta (prioridad máxima) | = 1 | = 1 | ✅ |
| 323 | Panel: casos en seguimiento | = 0 | = 0 | ✅ |
| 324 | Inventario: relojes vigentes con batería bajo 20 % en su último lote | = 1 | = 1 | ✅ |

### 18. Auditoría de cambios

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 325 | Ana modifica a María y cambia su configuración; queda en la auditoría | Se acepta | Aceptado | ✅ |
| 326 | Auditar la desactivación de un contacto, la ficha, una condición y un permiso, cada uno con su propio id | Se acepta | Aceptado | ✅ |
| 327 | Historial de un objeto con clave compuesta (configuración de María del 10-10) | = 1 | = 1 | ✅ |
| 328 | Borrar a una profesional con historial (se desactiva, no se borra) | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |

### 19. Borrado al terminar el piloto (4.4.5)

| # | Caso | Esperado | Obtenido | |
|---|---|---|---|---|
| 329 | Borrar un reloj asignado | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 330 | Borrar un vínculo que tiene avisos enviados | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 331 | Borrar una persona que es contacto de pacientes | Se rechaza (clave foránea) | Rechazado (clave foránea) | ✅ |
| 332 | Borrar a María con todos sus datos | Se acepta | Aceptado | ✅ |
| 333 | No queda ningún dato de María | = 0 | = 0 | ✅ |
| 334 | Pedro y Rosa conservan sus datos (5 vínculos + 8 alertas) | = 13 | = 13 | ✅ |
| 335 | La auditoría sobre María sobrevive al borrado | = 2 | = 2 | ✅ |
| 336 | Seguridad Ciudadana se conserva (sigue siendo contacto de Pedro y Rosa) | = 1 | = 1 | ✅ |
| 337 | Limpieza: el backend borra las personas que quedaron sin pacientes (no los contactos internos) | Se acepta | Aceptado | ✅ |
| 338 | Se borró solo la hija de María | = 3 / 0 | = 3 / 0 | ✅ |
| 339 | El contacto interno de Ana se conserva | = 1 | = 1 | ✅ |
| 340 | Se conservan profesionales, relojes y documentos de otros pacientes | = 4 / 5 | = 4 / 5 | ✅ |
