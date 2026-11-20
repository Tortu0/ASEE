package com.asee.deportivo.exception;

import com.asee.deportivo.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- Nuestras excepciones de negocio (Parte 6) ----------

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(PeticionInvalidaException.class)
    public ResponseEntity<ErrorResponse> peticionInvalida(PeticionInvalidaException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponse> conflicto(ConflictoException ex) {
        return respuesta(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, ex.getMessage(), List.of());
    }

    // ---------- Cualquier otro fallo inesperado ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception ex) {
        log.error("Error interno no controlado", ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", List.of());
    }

    // ---------- Validaciones de Spring: añadimos el detalle de cada campo ----------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describir)
                .toList();
        return cuerpo(HttpStatus.BAD_REQUEST, "Petición mal formada o datos no válidos", detalles);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<String> detalles = ex.getAllErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .toList();
        return cuerpo(HttpStatus.BAD_REQUEST, "Petición mal formada o datos no válidos", detalles);
    }

    // ---------- Resto de errores estándar de Spring MVC (UUID mal formado,
    // ---------- JSON ilegible, ruta inexistente, método no permitido...) ----------

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        return cuerpo(statusCode, mensajePara(statusCode), List.of());
    }

    // ---------- Utilidades ----------

    private String describir(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private String mensajePara(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Petición mal formada o datos no válidos";
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método no permitido para este recurso";
            case 415 -> "Tipo de contenido no soportado";
            default -> "No se ha podido procesar la petición";
        };
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String mensaje, List<String> detalles) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), mensaje, detalles));
    }

    private ResponseEntity<Object> cuerpo(HttpStatusCode status, String mensaje, List<String> detalles) {
        return ResponseEntity.status(status).body(new ErrorResponse(status.value(), mensaje, detalles));
    }
}