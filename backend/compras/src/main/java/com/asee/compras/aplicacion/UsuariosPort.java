package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Identidad;
import com.asee.compras.dominio.Socio;

public interface UsuariosPort {
    /**
     * GET /usuarios/{usuarioId}/socio. Lanza ApiException 404 si el usuario no existe y 503 si Usuarios
     * no está disponible (memoria 3.3 y 3.6: no se confirma un descuento que no se pueda validar).
     */
    Socio consultarSocio(Identidad quien, String usuarioId);
}
