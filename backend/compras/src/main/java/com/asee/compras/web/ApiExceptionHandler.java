package com.asee.compras.web;

import com.asee.compras.aplicacion.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Errores con el formato del contrato: {codigo, mensaje, detalles}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> negocio(ApiException e) {
        return respuesta(e.status, e.getMessage(), e.detalles);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> cuerpoIlegible(HttpMessageNotReadableException e) {
        return respuesta(400, "El cuerpo de la petición no es un JSON válido o tiene tipos incorrectos", List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> tipoIncorrecto(MethodArgumentTypeMismatchException e) {
        return respuesta(400, "El parámetro '" + e.getName() + "' tiene un valor no válido", List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> parametroAusente(MissingServletRequestParameterException e) {
        return respuesta(400, "Falta el parámetro '" + e.getParameterName() + "'", List.of());
    }

    private static ResponseEntity<Map<String, Object>> respuesta(int status, String mensaje, List<String> detalles) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("codigo", status);
        cuerpo.put("mensaje", mensaje);
        if (!detalles.isEmpty()) cuerpo.put("detalles", detalles);
        return ResponseEntity.status(status).body(cuerpo);
    }
}
