package cl.ucn.alertacuida.adapters.web.auditoria;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint cuya respuesta exitosa debe quedar como lectura en la auditoría. La registra
 * {@link RegistroDeLecturas}, así que el controlador no tiene que recordar hacerlo.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LecturaAuditada {

	/** @return qué tipo de dato se leyó (valores de registro_auditoria.entidad) */
	String entidad();

	/** @return la variable de la ruta con el identificador que se registra; vacío si se leyó una lista */
	String idVariable() default "";

}
