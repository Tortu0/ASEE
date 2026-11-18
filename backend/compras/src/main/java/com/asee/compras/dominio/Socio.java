package com.asee.compras.dominio;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Respuesta de Usuarios a GET /usuarios/{id}/socio: solo interesa si es socio activo y su descuento. */
public record Socio(String estado, BigDecimal descuentoPorcentaje) {

    private static final Pattern ESTADO = Pattern.compile("\"estado\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern DESCUENTO = Pattern.compile("\"descuentoPorcentaje\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

    public boolean activo() {
        return "activo".equals(estado);
    }

    /** Porcentaje a aplicar: solo si es socio activo. */
    public BigDecimal descuentoAplicable() {
        return activo() && descuentoPorcentaje != null ? descuentoPorcentaje : BigDecimal.ZERO;
    }

    /** Lectura mínima del JSON de Usuarios (sin depender de ninguna librería JSON). */
    public static Socio desdeJson(String json) {
        String texto = json == null ? "" : json;
        Matcher e = ESTADO.matcher(texto);
        Matcher d = DESCUENTO.matcher(texto);
        return new Socio(e.find() ? e.group(1) : "no_socio", d.find() ? new BigDecimal(d.group(1)) : BigDecimal.ZERO);
    }
}
