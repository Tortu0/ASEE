package com.asee.compras.web;

import com.asee.compras.aplicacion.Autenticador;
import com.asee.compras.dominio.Identidad;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Obtiene la identidad de la petición (Bearer de Usuarios o cabeceras X-User-* del gateway). Lanza 401 si no hay. */
@Component
public class IdentidadResolver {

    private final Autenticador autenticador;

    public IdentidadResolver(Autenticador autenticador) {
        this.autenticador = autenticador;
    }

    public Identidad quien(HttpServletRequest request) {
        return autenticador.resolver(request.getHeader("Authorization"), request.getHeader("X-User-Id"), request.getHeader("X-User-Role"));
    }
}
