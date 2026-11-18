package com.asee.entradas.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Extrae datos del usuario autenticado a partir del token JWT.
 *
 * El claim "sub" contiene el usuarioId. El rol se busca en los claims "role", "rol" y "roles"
 * (texto o lista), porque Usuarios llama "rol" a este dato en su API. Los roles de Usuarios
 * son: visitante, socio, staff y admin.
 */
public class JwtUser {

    private static final String[] CLAIMS_ROL = {"role", "rol", "roles"};

    private final String userId;
    private final Set<String> roles;

    private JwtUser(String userId, Set<String> roles) {
        this.userId = userId;
        this.roles = roles;
    }

    /** Construye un JwtUser a partir del {@link Authentication} de Spring Security. */
    public static JwtUser from(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalStateException("No hay usuario autenticado");
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String claim : CLAIMS_ROL) {
            Object valor = jwt.getClaim(claim);
            if (valor instanceof String s) {
                roles.add(normalizar(s));
            } else if (valor instanceof Collection<?> c) {
                c.forEach(x -> roles.add(normalizar(String.valueOf(x))));
            }
        }
        if (roles.isEmpty()) {
            roles.add("visitante");
        }
        return new JwtUser(jwt.getSubject(), roles);
    }

    private static String normalizar(String rol) {
        String r = rol.trim().toLowerCase();
        return r.startsWith("role_") ? r.substring(5) : r;
    }

    public String getUserId() { return userId; }

    /** Primer rol del token (compatibilidad con el código anterior). */
    public String getRole()   { return roles.iterator().next(); }

    public boolean isAdmin()  { return roles.contains("admin"); }
    public boolean isStaff()  { return roles.contains("staff") || isAdmin(); }
}