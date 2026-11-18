package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Identidad;

import java.time.Clock;
import java.util.UUID;

/**
 * Determina quién hace la petición.
 *  - Con JWT_SECRET configurado: exige el Bearer de Usuarios, comprueba su firma y IGNORA X-User-*.
 *  - Sin secreto: confía en X-User-Id / X-User-Role del API Gateway (memoria 3.1: la red interna impide el acceso
 *    externo directo). En ese modo el servicio NO debe ser alcanzable sin pasar por el gateway.
 */
public final class Autenticador {

    private final JwtHs256Verifier verificador;   // null = modo cabeceras del gateway

    public Autenticador(String jwtSecret, Clock reloj) {
        this.verificador = jwtSecret == null || jwtSecret.isBlank() ? null : new JwtHs256Verifier(jwtSecret, reloj);
    }

    public Identidad resolver(String authorization, String xUserId, String xUserRole) {
        String bearer = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        if (verificador != null) {
            if (bearer == null || bearer.isEmpty()) throw ApiException.noAutenticado("Falta el token de autenticación");
            return verificador.verificar(bearer);
        }
        if (xUserId == null || xUserId.isBlank()) throw ApiException.noAutenticado("Falta la identidad del usuario");
        try {
            UUID.fromString(xUserId);
        } catch (IllegalArgumentException e) {
            throw ApiException.noAutenticado("Identidad de usuario no válida");
        }
        String rol = xUserRole == null || xUserRole.isBlank() ? "visitante" : xUserRole.trim().toLowerCase();
        return new Identidad(xUserId, rol, bearer);
    }
}
