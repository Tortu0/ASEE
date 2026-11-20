package com.asee.deportivo.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

// Lectura de los datos del usuario que ha hecho la petición (ya validado por el JWT)
public final class UsuarioAutenticado {

    private UsuarioAutenticado() {
    }

    public static boolean esStaffOAdmin(Authentication auth) {
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority autoridad : auth.getAuthorities()) {
            String rol = autoridad.getAuthority();
            if ("ROLE_STAFF".equals(rol) || "ROLE_ADMIN".equals(rol)) {
                return true;
            }
        }
        return false;
    }

    // Identificador del usuario (claim "sub" del token); null si no es un UUID válido
    public static UUID id(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean esPropietario(Authentication auth, UUID usuarioId) {
        UUID id = id(auth);
        return id != null && id.equals(usuarioId);
    }
}