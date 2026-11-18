package com.asee.compras.aplicacion;

import java.util.List;

/** Error de negocio con su código HTTP. Se serializa con el formato del contrato: {codigo, mensaje, detalles}. */
public class ApiException extends RuntimeException {

    public final int status;
    public final List<String> detalles;

    public ApiException(int status, String mensaje, List<String> detalles) {
        super(mensaje);
        this.status = status;
        this.detalles = detalles == null ? List.of() : detalles;
    }

    public static ApiException invalida(String mensaje, String... detalles) { return new ApiException(400, mensaje, List.of(detalles)); }
    public static ApiException noAutenticado(String mensaje) { return new ApiException(401, mensaje, null); }
    public static ApiException pagoRechazado(String mensaje) { return new ApiException(402, mensaje, null); }
    public static ApiException prohibido(String mensaje) { return new ApiException(403, mensaje, null); }
    public static ApiException noEncontrado(String mensaje) { return new ApiException(404, mensaje, null); }
    public static ApiException conflicto(String mensaje) { return new ApiException(409, mensaje, null); }
    public static ApiException noDisponible(String mensaje) { return new ApiException(503, mensaje, null); }
}
