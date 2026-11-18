package com.asee.compras.dominio;

/** Usuario autenticado de la petición. {@code token} es el Bearer original (se reenvía a Usuarios); puede ser null. */
public record Identidad(String usuarioId, String rol, String token) {

    public boolean esStaff() {
        return "staff".equals(rol) || "admin".equals(rol);
    }

    public boolean esAdmin() {
        return "admin".equals(rol);
    }
}
