# Alertas: cómo funcionan y qué se decidió

Resumen de las alertas a la fecha (10-11-2026): qué las origina, quién hace cada cosa, qué reglas siguen, dónde se cambia cada valor y qué decisiones hay detrás.

Más detalle: [backend](backend.md), [modelo de datos](modelo-datos.md), [contrato con el reloj](api-reloj.md), [endpoints del reloj](endpoints-reloj.md) y [cronograma](cronograma.md).

## Quién hace qué

El reloj y el servidor son dos sistemas aparte, y la app del reloj está fuera del alcance de este repositorio.

| Reloj | Servidor |
|---|---|
| Evalúa sus lecturas con los parámetros del paciente y detecta la emergencia | Entrega esos parámetros en la configuración |
| Vibra 30 segundos para que el paciente pueda cancelar | Guarda cada alerta sin descartar ninguna y marca lo que no calza |
| Elige el contacto por prioridad y horario, espera y pasa al siguiente | Envía el WhatsApp o el correo que pide el reloj (la clave de Meta queda solo aquí) |
| Al terminar la lista, la repite una vez e informa `SIN_RESPUESTA` | Recibe la respuesta del contacto, avisa al reloj por FCM y registra el recorrido |
| | Crea las alertas técnicas y avisa a la oficina PRLAC |

Nadie llama automáticamente al SAMU. En las alertas graves, el mensaje le indica al contacto que llame al 131.

## Tipos de alerta

| Tipo | Qué significa | Lo detecta | Gravedad |
|---|---|---|---|
| `CAIDA` | Caída, con espera de inmovilidad | Reloj | La que envía el reloj; una leve o media puede subir a grave |
| `FC` / `SPO2` | Valor más allá del límite grave en 2 lecturas consecutivas | Reloj | La que envía el reloj (la matriz espera el Anexo B) |
| `FC_SPO2` | FC y SpO2 anormales a la vez | Reloj | Según la matriz FC × SpO2, pendiente del Anexo B |
| `TENDENCIA` | Tiempo fuera del rango normal sin llegar al límite grave | Reloj | `MEDIA` (configurable) |
| `INACTIVIDAD` | La persona no mueve el reloj | Reloj | La que envía el reloj |
| `ZONA_SEGURA` | Salió del círculo definido | Reloj | Elegida por paciente; media por defecto |
| `BOTON_AYUDA` | El paciente pidió ayuda | Reloj | Siempre `LEVE` |
| `SILENCIO_DATOS` | El reloj dejó de sincronizar | Solo el servidor | `MEDIA` |
| `BATERIA_BAJA` | La batería sigue baja | Servidor | `LEVE` |
| `RELOJ_NO_PUESTO` | Sigue sin ponérselo durante el día | Servidor | `LEVE` |
| `EXCESO_ALERTAS` | Demasiadas alertas de un reloj, posible falla | Solo el servidor | `LEVE` |

Las clínicas (de `CAIDA` a `BOTON_AYUDA`) escalan a los contactos del paciente. Las técnicas (las cuatro últimas) avisan a la oficina PRLAC.

El servidor solo puede crear alertas técnicas y simulacros: lo impide un `CHECK` de la base. Por eso hoy no puede crear una `INACTIVIDAD`. El contrato todavía permite que el reloj envíe `BATERIA_BAJA` y `RELOJ_NO_PUESTO` (pregunta 2 de [api-reloj.md](api-reloj.md)).

## Gravedad y a quién se avisa

La regla está fija en el código (`ReglaEnrutamiento`). El horario de cada contacto se respeta siempre, también en horario hábil.

| Gravedad | En horario hábil | Fuera del horario hábil |
|---|---|---|
| `LEVE` | Avisa a los contactos | No avisa: espera al día hábil (`espera_dia_habil`) |
| `MEDIA` | Avisa a los contactos | Avisa a los contactos disponibles a esa hora |
| `GRAVE` | Avisa a los contactos | Avisa a los contactos disponibles a esa hora |

- **Horario hábil:** lunes a jueves de 08:30 a 17:30 y viernes de 08:30 a 16:30, hora de Chile, sin feriados nacionales. El inicio cuenta y el fin no: a las 17:30 en punto ya es fuera de horario.
- Cada paciente debe tener contactos disponibles a toda hora, porque las medias y las graves avisan siempre.
- Con el botón de ayuda van primero los contactos `VECINO`, por prioridad, y después el resto, por prioridad.

## Reglas que aplica el reloj

El servidor solo entrega estos parámetros; quien los evalúa es la app del reloj, que aún debe programarlos.

### FC y SpO2

Valores base, provisionales hasta que los valide el equipo clínico. Cada paciente puede tener los suyos, que se editan en su ficha.

| | Grave bajo | Mínimo normal | Máximo normal | Grave alto |
|---|---|---|---|---|
| FC (lpm) | 40 | 50 | 110 | 130 |
| SpO2 (%) | 88 | 92 | Sin límite | Sin límite |

- "Sobre" y "bajo" son estrictos: FC 130 no es grave, 131 sí.
- El mínimo y el máximo normal cuentan como normales. Los límites graves cuentan como zona intermedia.
- Más allá del límite grave se exigen 2 lecturas consecutivas.
- Las lecturas se toman cada 30 minutos.

### Tendencia

- Alerta si FC o SpO2 lleva 120 minutos fuera del rango normal, sin llegar al límite grave. Valor provisional; 30 minutos es el mínimo posible.
- El tiempo parte de cero si una lectura vuelve al rango normal.
- Una sola alerta mientras el valor siga en esa zona. Puede volver a alertar solo después de una lectura normal.
- Módulo activo por defecto; se apaga por paciente.

### Zona segura

- Un círculo por paciente, con radio de 50 a 5000 metros, que el reloj revisa con su GPS. Exige el GPS activo.
- Viaja al reloj solo con el módulo activo. Las coordenadas se guardan cifradas y con historial, en la tabla `ZONA_SEGURA`.
- Cada cuánto se revisa el GPS lo decide la app del reloj. Ese módulo se había eliminado para cuidar la batería, así que el riesgo sigue vigente.

### Botón de ayuda

- Siempre `LEVE`, con los 30 segundos para cancelar. Como toda leve, fuera del horario hábil espera al día hábil.
- Activo por defecto.

## Recepción en el servidor

**El servidor nunca descarta una alerta por su contenido.** Solo responde 400 si el mensaje está mal formado.

- **Reintentos:** el `eventId` evita duplicados. Reenviar la misma alerta devuelve la misma respuesta (200 en vez de 201).
- **Correcciones al guardar:** una media o grave marcada "espera día hábil" se guarda sin la marca, y un `BOTON_AYUDA` con otra gravedad se guarda como leve.
- **Agravamiento:** solo una caída leve o media, no cancelada. Repetirlo no cambia la hora y se guardan los segundos de inmovilidad. Si es de otro reloj o no existe, 404; si no aplica, 409 `NO_APLICA`.
- **Cancelaciones:** el reloj las informa después, solo para métricas. No se comparan con la configuración y no cuentan para el exceso de alertas.

### Discrepancias

Lo que no calza con la configuración vigente se acepta y queda en la tabla `DISCREPANCIA_ALERTA`. Se guarda al recibir la alerta porque los valores base no tienen historial y después no se podría recalcular. La respuesta al reloj no cambia, y si la revisión misma falla, la alerta se guarda igual.

| Código | Cuándo |
|---|---|
| `VERSION_DISTINTA` | La versión que informa el reloj no es la vigente |
| `MODULO_APAGADO` | El módulo de ese tipo está apagado (`FC_SPO2` exige FC y SpO2) |
| `GRAVEDAD_DISTINTA` | Tendencia o zona segura con una gravedad distinta de la configurada |
| `LECTURAS_EN_RANGO` | FC o SpO2 sin ninguna lectura más allá del límite grave; tendencia y `FC_SPO2` con todas las lecturas en rango normal. Sin lecturas no se marca |

### Exceso de alertas

Desde la 10.ª alerta del mismo reloj en 60 minutos, contando la que llega, se crea una `EXCESO_ALERTAS`, una abierta a la vez. Ninguna alerta se rechaza por esto. No cuentan las canceladas, y una recibida hace justo 60 minutos ya no entra en la ventana. Si el equipo la cerró, puede crearse otra: esta no pasa por el vigilante, y solo aparece si el reloj sigue enviando alertas de más.

## Reglas de ausencia

El vigilante (`VigilarRelojes`) revisa cada 5 minutos todos los vínculos vigentes. Si falla la revisión de un reloj, registra el error y sigue con los demás. Cada regla da uno de tres resultados: crea la alerta, la cierra o no hace nada porque no alcanza para decidir.

| Alerta | Se crea cuando | Se cierra cuando |
|---|---|---|
| `SILENCIO_DATOS` | Pasan 4 horas sin sincronizar, contando desde el vínculo si nunca sincronizó. A las 4 horas justas ya hay silencio | Vuelve a sincronizar |
| `BATERIA_BAJA` | Las 2 últimas sincronizaciones están bajo 20 %. 20 % no es bajo | La última informa 20 % o más |
| `RELOJ_NO_PUESTO` | Las 2 últimas informan que no está puesto, ambas del mismo día, desde las 08:00 (incluida) hasta las 22:00 (excluida), hora de Chile | La última informa que está puesto |

- El horario evita alertar por la carga nocturna. Una sincronización nocturna sin poner no crea alertas, y tampoco cierra una abierta.
- Una sincronización que no informó el dato no crea ni cierra nada.
- El cierre automático queda sin responsable, y conserva a quien ya hubiera tomado la alerta.
- Mientras una siga abierta no se crea otra. Los simulacros no bloquean una alerta real ni se cierran con ella.
- Si una persona cerró la alerta y el problema sigue, no se crea otra. Vuelve a poder crearse cuando una sincronización posterior demuestre que el reloj se normalizó y el problema reaparezca.
- Las técnicas leves creadas fuera del horario hábil, incluida `EXCESO_ALERTAS`, quedan como `PENDIENTE_DIA_HABIL` para que el equipo las atienda el día hábil, aunque sea con días de retraso. El silencio de datos es media y no lleva esa marca.
- Si el servidor estuvo caído más de 4 horas, la primera revisión puede crear silencios que se cierran solos cuando los relojes vuelven a sincronizar.

## Aviso a la oficina PRLAC

Definido el 05-11-2026 y confirmado para las alertas técnicas el 08-11-2026. El envío aún no está programado (depende del canal de WhatsApp).

- WhatsApp a la vez, en paralelo al escalamiento del reloj, a todas las cuentas activas con rol `COORDINADOR` que tengan teléfono y opt-in vigente, sin opción de apagarlo.
- Solo en horario laboral y para todas las gravedades. Fuera de horario no se envía nada, ni siquiera al empezar la jornada: la alerta queda en el dashboard.
- Si un WhatsApp falla, se envía un correo de respaldo.
- Las demás cuentas reciben correo solo con `recibe_avisos`, que está apagado por defecto en las cuentas nuevas.
- Se define por rol, nunca con una cantidad fija de coordinadores.

### Correo institucional

Programado el 10-11-2026. Detalle técnico en [backend.md](backend.md#correo-institucional).

- **Técnicas y de salud van separadas.** A quién se avisa de una alerta de salud lo decide el reloj, que recorre los contactos del paciente; `recibe_avisos` no aplica a ellas. Las técnicas hablan del reloj y las avisa el servidor al equipo. La lista está en `TipoEvento.tecnicas()`.
- **A quién y cuándo:** a las cuentas activas con `recibe_avisos`, de cualquier rol (los coordinadores también, además de su WhatsApp), por cada alerta técnica, solo si ocurrió en horario hábil. No se avisa de simulacros ni de alertas resueltas.
- **Respaldo de WhatsApp:** cada WhatsApp fallido, a un contacto del paciente o a una cuenta del equipo, genera un correo al mismo destino, si tiene correo, a cualquier hora.
- **Reintentos:** a los 1, 5 y 15 minutos; después queda fallido, con el motivo, en `registro_intento`.
- **Contenido:** sin datos clínicos. Va el alias del paciente o, si no tiene, el folio de la alerta. Al contacto de una alerta grave se le pide llamar al 131.
- **Registro:** cada correo queda en `registro_intento`. Las cuentas del equipo figuran como **contacto interno**, que es solo un enlace a su cuenta y nunca entra en la lista que recorre el reloj. Estos avisos no cambian el estado de contacto de la alerta.

## Estado y cierre

El estado no se guarda: se calcula con la vista `ALERTA_ESTADO`. `ALERTA` es la evidencia de lo que pasó, y `ATENCION_ALERTA` es lo que hace el equipo. Sin fila en esa tabla, la alerta está sin asignar.

| Estado de contacto | Cuándo |
|---|---|
| `CANCELADA` | El paciente la canceló |
| `RESUELTA` | La atención tiene fecha de cierre |
| `CONFIRMADA` | Un contacto respondió "Voy en camino" |
| `SIN_RESPUESTA` | El reloj terminó la lista dos veces |
| `AVISANDO_CONTACTO` | Hay al menos un aviso |
| `PENDIENTE_DIA_HABIL` | Leve fuera de horario, a la espera del día hábil |
| `RECIBIDA` | Ninguna de las anteriores |

El estado de atención es `SIN_ASIGNAR`, `EN_ATENCION` o `RESUELTA`. Al cerrar, una alerta clínica se clasifica como `POSITIVA` o `FALSA_ALARMA`; una positiva abre su seguimiento.

## Dónde se cambia cada valor

Los de `application.properties` requieren reiniciar el backend.

| Qué | Hoy | Dónde |
|---|---|---|
| Umbrales base de FC y SpO2 | Tabla de arriba | `alertacuida.clinica.umbral-*` en `application.properties` |
| Umbrales de un paciente | Los propios, o los valores base | Ficha del paciente en el dashboard |
| Minutos de tendencia | 120 | `alertacuida.clinica.minutos-tendencia` |
| Gravedad de tendencia | `MEDIA` | `alertacuida.clinica.severidad-tendencia` |
| Minutos de espera por contacto | 5 | `alertacuida.clinica.minutos-espera` |
| Exceso de alertas | 10 en 60 minutos | `alertacuida.alertas.avisar-desde` y `ventana-minutos` |
| Revisión del vigilante | Cada 5 minutos | `alertacuida.vigilancia.intervalo` (`PT5M`) |
| Silencio de datos | 4 horas | `alertacuida.vigilancia.silencio` |
| Batería baja | Bajo 20 % | `alertacuida.vigilancia.bateria-minima` |
| Horario de reloj no puesto | 08:00 a 22:00 | `alertacuida.vigilancia.hora-inicio-dia` y `hora-fin-dia` |
| Sincronizaciones seguidas | 2 | Constante `VigilarRelojes.LOTES_SEGUIDOS` en el código |
| Reintentos del correo | 1, 5 y 15 minutos | `alertacuida.correo.reintentos` |
| Antigüedad máxima de lo que se avisa por correo | 1 hora | `alertacuida.correo.ventana` |
| Qué alertas van por correo a `recibe_avisos` | Las técnicas | `TipoEvento.tecnicas()` |
| Texto de los correos | Ver arriba | `PlantillasCorreo` |
| Horario hábil y feriados | Ver arriba | `app/src/main/resources/calendario.yml` |
| Gravedad de cada técnica | Tabla de tipos | Código de cada regla (`ReglaSilencioDatos`, `ReglaBateriaBaja`, `ReglaRelojNoPuesto`) y `RecibirAlerta` |

## Decisiones

"Confirmada" es una decisión del equipo. "Sin confirmar" es una elección técnica que se tomó al programar y que el equipo todavía no ha validado de forma explícita. Los valores clínicos son provisionales hasta que los valide el equipo clínico.

| Fecha | Decisión | Por qué | Estado |
|---|---|---|---|
| 15-10-2026 | El reloj tiene LTE: decide y escala. El servidor envía los avisos | Reemplaza el escalamiento en el servidor de la v6; la clave de Meta no sale del servidor | Confirmada |
| 15-10-2026 | Avisos solo por WhatsApp (con opt-in) y correo; sin llamadas ni SMS | | Confirmada |
| 05-11-2026 | Tendencia por tiempo fuera del rango normal: 120 minutos, alerta media | Reemplaza parámetros que esperaban el Anexo B | Confirmada, valor provisional |
| 05-11-2026 | Una sola alerta de tendencia hasta volver a lo normal | | Confirmada, sujeta a validación clínica |
| 05-11-2026 | Vuelve la zona segura, con gravedad por paciente | Revierte la eliminación de la v7 | Confirmada |
| 05-11-2026 | Botón de ayuda leve, primero a vecinos | Revierte "sin botón de pánico" | Confirmada |
| 05-11-2026 | Radio de zona de 50 a 5000 m; botón activo por defecto; la zona viaja solo con el módulo activo | | Sin confirmar |
| 05-11-2026 | Botón con otra gravedad se guarda como leve | No perder un pedido de ayuda | Sin confirmar |
| 05-11-2026 | Aviso a todo `COORDINADOR` por WhatsApp, solo en horario laboral, con correo de respaldo | Que a la oficina le lleguen siempre a la vez | Confirmada |
| 05-11-2026 | `recibe_avisos` apagado por defecto | | Confirmada |
| 05-11-2026 | Se revisan cuatro discrepancias y se guardan en una tabla | No se pueden recalcular después | Confirmada |
| 05-11-2026 | Umbrales "sobre" y "bajo" estrictos | | Confirmada |
| 05-11-2026 | Exceso desde la 10.ª alerta en 60 minutos, sin cancelaciones; justo 60 minutos no entra | Detectar un reloj defectuoso sin perder alertas | Confirmada |
| 05-11-2026 | El agravamiento guarda los segundos de inmovilidad en una columna nueva | | Confirmada |
| 05-11-2026 | Las cancelaciones no se comparan con la configuración | Solo sirven para métricas | Sin confirmar |
| 05-11-2026 | Si la revisión de discrepancias falla, la alerta se guarda igual | Guardarla importa más que revisarla | Sin confirmar |
| 08-11-2026 | Inactividad del servidor pospuesta | La sincronización no trae datos de movimiento | Confirmada |
| 08-11-2026 | Silencio: 4 horas, media. Batería y no puesto: leves | | Confirmada |
| 08-11-2026 | Reloj no puesto: 2 sincronizaciones seguidas, solo de día | Carga nocturna | Confirmada |
| 08-11-2026 | Batería baja: 2 sincronizaciones seguidas bajo 20 % | Confirma que el estado persiste, con margen para reaccionar | Confirmada |
| 08-11-2026 | Las tres técnicas se cierran solas al volver a la normalidad | | Confirmada |
| 08-11-2026 | Las alertas técnicas siguen la misma regla: WhatsApp a los coordinadores, con correo de respaldo | Aclara el cronograma, que aún decía solo correo | Confirmada |
| 08-11-2026 | Tras un cierre a mano no se crea otra alerta hasta que el reloj se normalice y el problema reaparezca | Evita repetir el WhatsApp a todos los coordinadores | Confirmada |
| 08-11-2026 | Las técnicas leves fuera de horario quedan pendientes para el día hábil | Las alertas siempre deben responderse, aunque sea con días de retraso | Confirmada |
| 08-11-2026 | Una técnica leve pendiente del día hábil también se cierra sola si el reloj se normaliza | El problema ya se resolvió y no deja trabajo al equipo | Confirmada |
| 08-11-2026 | Vigilante cada 5 minutos; reloj sin sincronizaciones medido desde el vínculo; a las 4 horas justas ya hay silencio; sin el dato no se decide | | Sin confirmar |
| 08-11-2026 | Correo: 3 reintentos a los 1, 5 y 15 minutos; después, fallido y visible | | Confirmada |
| 08-11-2026 | Correo a las cuentas con `recibe_avisos` solo en horario laboral | Igual que los coordinadores | Confirmada |
| 10-11-2026 | Las cuentas del equipo se registran como contacto interno en `CONTACTO`, solo como enlace a la cuenta | No duplicar datos y que el reloj no recorra al equipo | Confirmada |
| 10-11-2026 | Sin alias, el mensaje usa el folio de la alerta | | Confirmada |
| 10-11-2026 | Correo con Spring Mail; pruebas con GreenMail y Mailpit en local | Ningún correo real en desarrollo | Confirmada |
| 10-11-2026 | `recibe_avisos` es solo para las alertas técnicas; las de salud las enruta el reloj | Separar técnicas de salud: el reloj decide a quién avisar | Confirmada |
| 10-11-2026 | Los coordinadores con `recibe_avisos` también reciben el correo, además del WhatsApp | | Confirmada |
| 10-11-2026 | El respaldo de WhatsApp sale para cualquier alerta y a cualquier hora, si el destino tiene correo | Un aviso que no llegó debe intentar otra vía | Sin confirmar |
| 10-11-2026 | Solo se avisa lo ocurrido en la última hora | No avisar de alertas viejas tras encender `recibe_avisos` o tras una caída del servidor | Sin confirmar |
| 10-11-2026 | Solo un contacto del paciente puede confirmar ("Voy en camino") | El estado que consulta el reloj depende del contacto que confirmó | Sin confirmar |

## Qué falta

**Por programar en el servidor** (tareas del [cronograma](cronograma.md)):

- Avisos al reloj por FCM (2.3).
- Mostrar en el dashboard los correos fallidos (hoy quedan en `registro_intento`).
- Registro de cada paso y resolución de una alerta por un profesional (2.5).
- Red de seguridad sobre las lecturas (2.7) y simulacro (2.8).
- Canal de WhatsApp (2.9) y escalamiento del lado del servidor (2.10), que incluye el aviso a la oficina.
- Pantalla de alertas y discrepancias (3.4). Hoy las alertas solo se consultan por la API.
- Editar el teléfono y el opt-in de una cuenta ya creada, y registrar la baja del opt-in de un coordinador por webhook.

**Por definir:**

1. Inactividad como respaldo del servidor (pregunta 11 de [api-reloj.md](api-reloj.md)).
2. Quién envía `BATERIA_BAJA` y `RELOJ_NO_PUESTO`. Una enviada por el reloj no se cierra sola y, mientras siga abierta, el servidor no crea otra (pregunta 2).
3. Con quien desarrolla el reloj: cada cuánto revisa el GPS, cuántas posiciones fuera del círculo exige, si vuelve a alertar al regresar, y dónde va el botón y cómo evitar presionarlo por error (preguntas 9 y 10).
4. Matriz FC × SpO2, pendiente del Anexo B, y validación clínica de todos los valores provisionales.

## Pruebas

- `RecibirAlertaTest`, `RevisionDiscrepanciasTest`, `VigilarRelojesTest`, `PlantillasCorreoTest`, `EncolarCorreosTest` y `EnviarCorreosPendientesTest`: unitarias, sin base de datos.
- `RequisitosRecepcionAlertasTest`, `RequisitosReglasAusenciaTest` y `RequisitosCorreoTest`: contra PostgreSQL real, diseñadas desde los requisitos. La del correo usa GreenMail, sin enviar correos reales. Sin Docker se omiten.
- `ApiRelojTest` cubre la API del reloj, y `ModeloDatosTest` las reglas de la base.
