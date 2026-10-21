package cl.ucn.alertacuida.application.sesion.puerto;

import cl.ucn.alertacuida.application.sesion.TokenAcceso;
import cl.ucn.alertacuida.application.sesion.UsuarioSesion;

public interface EmisorToken {

	TokenAcceso emitir(UsuarioSesion usuario);

}
