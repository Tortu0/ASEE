package com.asee.entradas.controller;

import com.asee.entradas.dto.EntradaDto;
import com.asee.entradas.security.JwtUser;
import com.asee.entradas.service.EntradasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint de control de acceso al estadio: validación de entradas (staff).
 */
@RestController
@RequestMapping("/entradas")
@RequiredArgsConstructor
public class AccesoController {

    private final EntradasService service;

    /**
     * POST /v1/entradas/{entradaId}/validacion
     * Valida una entrada en el acceso al estadio y la marca como usada.
     */
    @PostMapping("/{entradaId}/validacion")
    public ResponseEntity<EntradaDto> validarEntrada(
            @PathVariable String entradaId,
            Authentication auth) {

        JwtUser caller = JwtUser.from(auth);
        EntradaDto entrada = service.validarEntrada(entradaId, caller.isStaff());
        return ResponseEntity.ok(entrada);
    }
}
