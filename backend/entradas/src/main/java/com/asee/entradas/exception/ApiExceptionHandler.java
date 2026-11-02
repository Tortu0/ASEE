package com.asee.entradas.exception;

import com.asee.entradas.dto.ErrorDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Manejador global de excepciones que las traduce a respuestas JSON con el
 * esquema {@code Error} definido en el OpenAPI.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorDto> handleApi(ApiException ex) {
        ErrorDto body = new ErrorDto(ex.getStatus().value(), ex.getMessage(), null);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        ErrorDto body = new ErrorDto(400, "Petición no válida", detalles);
        return ResponseEntity.badRequest().body(body);
    }

    /**
     * JSON mal formado, enum desconocido en el cuerpo (p. ej. tipoEntrada=xxx),
     * parámetro de consulta con tipo inválido (p. ej. ?estado=xxx) o parámetro obligatorio ausente.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorDto> handleBadRequest(Exception ex) {
        log.debug("Petición mal formada: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(new ErrorDto(400, "Petición mal formada", null));
    }

    /** Saltó un índice único de MongoDB: otro proceso se nos adelantó. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorDto> handleDuplicate(DuplicateKeyException ex) {
        log.warn("Clave duplicada en MongoDB: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorDto(409, "Conflicto: el recurso ya existe o fue procesado por otra petición", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGeneral(Exception ex) {
        log.error("Error no controlado", ex);
        ErrorDto body = new ErrorDto(500, "Error interno del servidor", null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}