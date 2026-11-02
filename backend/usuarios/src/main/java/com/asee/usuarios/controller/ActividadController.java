package com.asee.usuarios.controller;

import com.asee.usuarios.dto.ActividadResponseDTO;
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
}