package com.asee.estadisticas.exception;

import com.asee.estadisticas.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException ex) { return error(ex.status(), ex.getMessage()); }
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> invalid(ConstraintViolationException ex) { return error(400, "Parametros no validos"); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ErrorResponse> database(DataAccessException ex) { return error(503, "Almacenamiento no disponible"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        LoggerFactory.getLogger(getClass()).error("Error no controlado", ex);
        return error(500, "Error interno del servidor");
    }
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(new ErrorResponse(status.value(),
                status.value() == 404 ? "Recurso no encontrado" : "Peticion o parametros no validos", List.of()), headers, status);
    }
    private ResponseEntity<ErrorResponse> error(int status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(status, message, List.of()));
    }
}
