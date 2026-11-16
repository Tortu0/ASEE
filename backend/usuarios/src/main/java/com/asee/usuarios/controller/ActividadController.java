package com.asee.usuarios.controller;

import com.asee.usuarios.dto.ActividadResponseDTO;
import com.asee.usuarios.dto.ActividadCreateDTO;
import com.asee.usuarios.dto.ErrorDTO;
import com.asee.usuarios.service.ActividadService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
public class ActividadController {

    private final ActividadService actividadService;

    public ActividadController(ActividadService actividadService) {
        this.actividadService = actividadService;
    }

    // Mantenemos la ruta "/historial" intacta para cumplir con el YAML
    @GetMapping("/{usuarioId}/historial")
    public ResponseEntity<?> consultarHistorial(
            @PathVariable UUID usuarioId,
            @RequestHeader("X-User-Id") UUID headerUserId,
            @RequestHeader("X-User-Role") String headerUserRole) {

        // 1. CONTROL DE ACCESO (Autorización): Solo el dueño, o el personal del sistema pueden verlo
        boolean esAdminOStaff = headerUserRole.equals("admin") || headerUserRole.equals("staff");
        boolean esMismoUsuario = headerUserId.equals(usuarioId);

        if (!esAdminOStaff && !esMismoUsuario) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorDTO(403, "No tienes permisos para consultar este historial"));
        }

        try {
            // 2. RECUPERAR DATOS Y RESPONDER (200 OK)
            List<ActividadResponseDTO> historial = actividadService.obtenerHistorialUsuario(usuarioId);
            return ResponseEntity.ok(historial);
        } catch (IllegalArgumentException e) {
            // 3. MANEJO DE ERRORES: Usuario no existe (404)
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorDTO(404, e.getMessage()));
        }
    }

    // POST /usuarios/{usuarioId}/historial (solo admin): alta manual de una actividad
    @PostMapping("/{usuarioId}/historial")
    public ResponseEntity<?> registrarActividad(
            @PathVariable UUID usuarioId,
            @RequestBody ActividadCreateDTO dto,
            @RequestHeader("X-User-Role") String headerUserRole) {

        if (!headerUserRole.equals("admin")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorDTO(403, "Solo los administradores pueden registrar actividades"));
        }
        if (dto.getTipo() == null || !(dto.getTipo().equals("entrada") || dto.getTipo().equals("pedido"))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorDTO(400, "El campo 'tipo' debe ser 'entrada' o 'pedido'"));
        }
        if (dto.getReferenciaId() == null || dto.getReferenciaId().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorDTO(400, "El campo 'referenciaId' es obligatorio"));
        }
        java.time.LocalDateTime fecha = ActividadService.parseFecha(dto.getFecha());
        if (fecha == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorDTO(400, "El campo 'fecha' es obligatorio y debe ser una fecha-hora ISO-8601"));
        }
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(actividadService.registrarManual(usuarioId, dto, fecha));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorDTO(404, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorDTO(409, e.getMessage()));
        }
    }
}
