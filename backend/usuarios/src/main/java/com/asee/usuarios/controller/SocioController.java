package com.asee.usuarios.controller;

import com.asee.usuarios.dto.ErrorDTO;
import com.asee.usuarios.dto.SocioResponseDTO;
import com.asee.usuarios.dto.SocioUpdateDTO;
import com.asee.usuarios.entity.Socio;
import com.asee.usuarios.service.SocioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
public class SocioController {

    private final SocioService socioService;

    public SocioController(SocioService socioService) {
        this.socioService = socioService;
    }

    @GetMapping("/{usuarioId}/socio")
    public ResponseEntity<?> obtenerSocio(
            @PathVariable UUID usuarioId,
            @RequestHeader("X-User-Id") UUID headerUserId,
            @RequestHeader("X-User-Role") String headerUserRole) {

        // 1. SEGURIDAD: Un usuario solo puede ver su propio estado; admin/staff ven todos.
        boolean esAdminOStaff = headerUserRole.equals("admin") || headerUserRole.equals("staff");
        boolean esMismoUsuario = headerUserId.equals(usuarioId);

        if (!esAdminOStaff && !esMismoUsuario) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorDTO(403, "No tienes permisos para ver el estado de socio de este perfil"));
        }

        try {
            Socio socio = socioService.obtenerEstadoSocio(usuarioId);
            return ResponseEntity.ok(mapearSocioADTO(socio));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorDTO(404, e.getMessage()));
        }
    }

    @PutMapping("/{usuarioId}/socio")
    public ResponseEntity<?> actualizarSocio(
            @PathVariable UUID usuarioId,
            @RequestBody SocioUpdateDTO dto,
            @RequestHeader("X-User-Role") String headerUserRole) {

        // 2. SEGURIDAD: el contrato permite dar de alta y renovar socios a staff y admin.
        if (!headerUserRole.equals("admin") && !headerUserRole.equals("staff")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorDTO(403, "Solo el personal (staff o admin) puede gestionar las cuotas de los socios"));
        }

        try {
            Socio socioActualizado = socioService.actualizarSocio(usuarioId, dto);
            return ResponseEntity.ok(mapearSocioADTO(socioActualizado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorDTO(404, e.getMessage()));
        }
    }

    // Método auxiliar privado para centralizar la conversión y mantener los endpoints limpios
    private SocioResponseDTO mapearSocioADTO(Socio socio) {
        SocioResponseDTO response = new SocioResponseDTO();
        response.setUsuarioId(socio.getUsuarioId());
        response.setNumeroSocio(socio.getNumeroSocio());
        response.setEstado(socio.getEstado());
        response.setTipoCuota(socio.getTipoCuota());
        response.setFechaAlta(socio.getFechaAlta());
        response.setFechaCaducidad(socio.getFechaCaducidad());
        response.setDescuentoPorcentaje(socio.getDescuentoPorcentaje());
        return response;
    }
}