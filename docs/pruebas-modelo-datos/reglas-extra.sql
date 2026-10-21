-- Reglas del modelo que DBML no dibuja, copiadas de las notas de cada tabla en ../modelo-datos.dbml.
-- Se aplican después de crear las tablas. Son la base de las migraciones de la tarea 1.1.

-- Índices únicos parciales
CREATE UNIQUE INDEX contacto_paciente_prioridad ON "CONTACTO_PACIENTE" (id_paciente, orden_prioridad) WHERE activo;
CREATE UNIQUE INDEX contacto_paciente_cuidador ON "CONTACTO_PACIENTE" (id_paciente) WHERE es_cuidador_principal AND activo;

-- Asignaciones sin superposición en el tiempo, vigentes o cerradas (fecha_fin vacía = rango abierto)
CREATE EXTENSION IF NOT EXISTS btree_gist;
ALTER TABLE "ASIGNACION_RELOJ" ADD CONSTRAINT asignacion_reloj_sin_cruce
  EXCLUDE USING gist (id_dispositivo WITH =, tstzrange(fecha_inicio, fecha_fin) WITH &&);
ALTER TABLE "ASIGNACION_RELOJ" ADD CONSTRAINT asignacion_paciente_sin_cruce
  EXCLUDE USING gist (id_paciente WITH =, tstzrange(fecha_inicio, fecha_fin) WITH &&);

-- Clave diferida: permite borrar al paciente completo, pero no un vínculo suelto con intentos
ALTER TABLE "REGISTRO_INTENTO" ALTER CONSTRAINT "REGISTRO_INTENTO_id_contacto_paciente_fkey" DEFERRABLE INITIALLY DEFERRED;

-- CHECK
ALTER TABLE "PACIENTE" ADD CONSTRAINT paciente_fechas CHECK (fecha_baja IS NULL OR fecha_baja >= fecha_ingreso);

ALTER TABLE "CONTACTO" ADD CONSTRAINT contacto_canal CHECK (whatsapp IS NOT NULL OR correo IS NOT NULL);
ALTER TABLE "CONTACTO" ADD CONSTRAINT contacto_whatsapp_hash CHECK ((whatsapp IS NULL) = (whatsapp_hash IS NULL));
ALTER TABLE "CONTACTO" ADD CONSTRAINT contacto_correo_hash CHECK ((correo IS NULL) = (correo_hash IS NULL));
ALTER TABLE "CONTACTO" ADD CONSTRAINT contacto_opt_in CHECK (fecha_revocacion_opt_in IS NULL OR fecha_opt_in IS NOT NULL);

ALTER TABLE "CONTACTO_PACIENTE" ADD CONSTRAINT contacto_paciente_horario CHECK ((hora_inicio IS NULL) = (hora_fin IS NULL));
ALTER TABLE "CONTACTO_PACIENTE" ADD CONSTRAINT contacto_paciente_horario_distinto CHECK (hora_inicio IS NULL OR hora_inicio <> hora_fin);
ALTER TABLE "CONTACTO_PACIENTE" ADD CONSTRAINT contacto_paciente_prioridad_positiva CHECK (orden_prioridad >= 1);
ALTER TABLE "CONTACTO_PACIENTE" ADD CONSTRAINT contacto_paciente_parentesco CHECK (parentesco IS NULL OR codigo_tipo_contacto = 'FAMILIA');

ALTER TABLE "CONFIGURACION" ADD CONSTRAINT configuracion_espera CHECK (minutos_espera > 0);

ALTER TABLE "UMBRAL" ADD CONSTRAINT umbral_completo CHECK (
  (grave_bajo IS NULL AND normal_min IS NULL AND normal_max IS NULL AND grave_alto IS NULL)
  OR (grave_bajo IS NOT NULL AND normal_min IS NOT NULL AND (
        (variable = 'SPO2' AND normal_max IS NULL AND grave_alto IS NULL)
     OR (variable <> 'SPO2' AND normal_max IS NOT NULL AND grave_alto IS NOT NULL))));
ALTER TABLE "UMBRAL" ADD CONSTRAINT umbral_orden CHECK (
  grave_bajo IS NULL OR (grave_bajo < normal_min AND (normal_max IS NULL OR (normal_min < normal_max AND normal_max < grave_alto))));
ALTER TABLE "UMBRAL" ADD CONSTRAINT umbral_spo2 CHECK (variable <> 'SPO2' OR normal_min IS NULL OR normal_min <= 100);

ALTER TABLE "ASIGNACION_RELOJ" ADD CONSTRAINT asignacion_fechas CHECK (fecha_fin IS NULL OR fecha_fin > fecha_inicio);

ALTER TABLE "SINCRONIZACION" ADD CONSTRAINT sincronizacion_bateria CHECK (nivel_bateria BETWEEN 0 AND 100);
ALTER TABLE "SINCRONIZACION" ADD CONSTRAINT sincronizacion_minutos_puesto CHECK (minutos_puesto >= 0);

ALTER TABLE "EVENTO_DESCARTADO" ADD CONSTRAINT evento_descartado_cantidad CHECK (cantidad > 0);
ALTER TABLE "EVENTO_DESCARTADO" ADD CONSTRAINT evento_descartado_tipo CHECK (tipo_evento <> 'SILENCIO_DATOS');

ALTER TABLE "ALERTA" ADD CONSTRAINT alerta_cancelada CHECK (fecha_hora_cancelacion IS NULL OR fecha_hora_sin_respuesta IS NULL);
ALTER TABLE "ALERTA" ADD CONSTRAINT alerta_origen CHECK ((origen = 'SERVIDOR') = (hash_configuracion IS NULL));
ALTER TABLE "ALERTA" ADD CONSTRAINT alerta_agravamiento CHECK (fecha_hora_agravamiento IS NULL OR (tipo_evento = 'CAIDA' AND severidad <> 'GRAVE'));
ALTER TABLE "ALERTA" ADD CONSTRAINT alerta_espera_leve CHECK (NOT espera_dia_habil OR severidad = 'LEVE');
ALTER TABLE "ALERTA" ADD CONSTRAINT alerta_servidor_tecnica CHECK (origen = 'RELOJ' OR es_simulacro OR tipo_evento IN ('SILENCIO_DATOS', 'BATERIA_BAJA', 'RELOJ_NO_PUESTO'));
ALTER TABLE "ALERTA" ADD CONSTRAINT alerta_silencio_servidor CHECK (tipo_evento <> 'SILENCIO_DATOS' OR origen = 'SERVIDOR');

ALTER TABLE "ATENCION_ALERTA" ADD CONSTRAINT atencion_no_vacia CHECK (fecha_hora_toma IS NOT NULL OR fecha_hora_cierre IS NOT NULL);
ALTER TABLE "ATENCION_ALERTA" ADD CONSTRAINT atencion_toma CHECK ((fecha_hora_toma IS NULL) = (id_usuario_responsable IS NULL));
ALTER TABLE "ATENCION_ALERTA" ADD CONSTRAINT atencion_cierre CHECK (fecha_hora_cierre IS NOT NULL OR (id_usuario_resolutor IS NULL AND clasificacion IS NULL AND observaciones_cierre IS NULL));
ALTER TABLE "ATENCION_ALERTA" ADD CONSTRAINT atencion_orden CHECK (fecha_hora_cierre IS NULL OR fecha_hora_toma IS NULL OR fecha_hora_cierre >= fecha_hora_toma);
ALTER TABLE "ATENCION_ALERTA" ADD CONSTRAINT atencion_positiva CHECK (clasificacion IS DISTINCT FROM 'POSITIVA' OR id_usuario_responsable IS NOT NULL);
ALTER TABLE "ATENCION_ALERTA" ADD CONSTRAINT atencion_seguimiento CHECK (fecha_cierre_seguimiento IS NULL OR (clasificacion = 'POSITIVA' AND fecha_cierre_seguimiento >= fecha_hora_cierre));

ALTER TABLE "REGISTRO_INTENTO" ADD CONSTRAINT intento_ronda CHECK (ronda IN (1, 2));
ALTER TABLE "REGISTRO_INTENTO" ADD CONSTRAINT intento_respuesta CHECK ((respuesta IS NULL) = (fecha_hora_respuesta IS NULL));
ALTER TABLE "REGISTRO_INTENTO" ADD CONSTRAINT intento_fallo CHECK (motivo_fallo IS NULL OR fecha_hora_fallo IS NOT NULL);
ALTER TABLE "REGISTRO_INTENTO" ADD CONSTRAINT intento_lectura CHECK (fecha_hora_lectura IS NULL OR canal = 'WHATSAPP');

ALTER TABLE "AVISO_FCM" ADD CONSTRAINT aviso_fcm_intentos CHECK (intentos >= 0);

ALTER TABLE "SEGUIMIENTO" ADD CONSTRAINT seguimiento_destino CHECK ((tipo = 'DERIVACION') = (codigo_destino IS NOT NULL));

ALTER TABLE "USUARIO" ADD CONSTRAINT usuario_activo CHECK (NOT activo OR hash_contrasena IS NOT NULL);
ALTER TABLE "USUARIO" ADD CONSTRAINT usuario_correo_minusculas CHECK (correo = lower(correo));

ALTER TABLE "TOKEN_SESION" ADD CONSTRAINT token_sesion_vigencia CHECK (fecha_expiracion > fecha_emision);
ALTER TABLE "TOKEN_ACCION" ADD CONSTRAINT token_accion_vigencia CHECK (fecha_expiracion > fecha_emision);

ALTER TABLE "REGISTRO_AUDITORIA" ADD CONSTRAINT auditoria_entidad CHECK ((entidad IS NULL) = (id_entidad IS NULL));

-- Vista: lo que se calcula en vez de guardarse (nota de ALERTA)
CREATE VIEW "ALERTA_ESTADO" AS
SELECT a.id_alerta,
  CASE
    WHEN a.fecha_hora_cancelacion IS NOT NULL THEN 'CANCELADA'
    WHEN at.fecha_hora_cierre IS NOT NULL THEN 'RESUELTA'
    WHEN EXISTS (SELECT 1 FROM "REGISTRO_INTENTO" i WHERE i.id_alerta = a.id_alerta AND i.respuesta = 'VOY_EN_CAMINO') THEN 'CONFIRMADA'
    WHEN a.fecha_hora_sin_respuesta IS NOT NULL THEN 'SIN_RESPUESTA'
    WHEN EXISTS (SELECT 1 FROM "REGISTRO_INTENTO" i WHERE i.id_alerta = a.id_alerta) THEN 'AVISANDO_CONTACTO'
    WHEN a.espera_dia_habil THEN 'PENDIENTE_DIA_HABIL'
    ELSE 'RECIBIDA'
  END AS estado_contacto,
  CASE
    WHEN at.clasificacion = 'POSITIVA' AND at.fecha_cierre_seguimiento IS NULL THEN 'EN_SEGUIMIENTO'
    WHEN at.fecha_hora_cierre IS NOT NULL THEN 'RESUELTA'
    WHEN at.fecha_hora_toma IS NOT NULL THEN 'EN_ATENCION'
    ELSE 'SIN_ASIGNAR'
  END AS estado_atencion,
  CASE WHEN a.fecha_hora_agravamiento IS NOT NULL THEN 'GRAVE' ELSE a.severidad::text END AS severidad_actual
FROM "ALERTA" a LEFT JOIN "ATENCION_ALERTA" at USING (id_alerta);
