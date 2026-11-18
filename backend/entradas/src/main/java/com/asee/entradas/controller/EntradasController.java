package com.asee.entradas.controller;

import com.asee.entradas.dto.CompraEntradas;
import com.asee.entradas.dto.EntradaDto;
import com.asee.entradas.security.JwtUser;
import com.asee.entradas.service.EntradasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de entradas: compra, listado, consulta individual y anulación.
 * Todos requieren autenticación JWT.
 */
@RestController
@RequestMapping("/entradas")
@RequiredArgsConstructor
public class EntradasController {

    private final EntradasService service;

    /**
     * POST /v1/entradas
     * Compra entradas a partir de una reserva vigente.
     */
    @PostMapping
    public ResponseEntity<List<EntradaDto>> comprarEntradas(
            @Valid @RequestBody CompraEntradas req,
            Authentication auth) {

        JwtUser caller = JwtUser.from(auth);
        List<EntradaDto> entradas = service.comprarEntradas(req, caller.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(entradas);
    }

    /**
     * GET /v1/entradas?usuarioId=&partidoId=
     * Lista las entradas de un usuario (con filtro opcional por partido).
     */
    @GetMapping
    public ResponseEntity<List<EntradaDto>> listarEntradas(
            @RequestParam String usuarioId,
            @RequestParam(required = false) String partidoId,
            Authentication auth) {

        JwtUser caller = JwtUser.from(auth);
        List<EntradaDto> entradas = service.listarEntradas(
                usuarioId, partidoId, caller.getUserId(), caller.isStaff());
        return ResponseEntity.ok(entradas);
    }

    /**
     * GET /v1/entradas/{entradaId}
     * Obtiene una entrada con su código QR.
     */
    @GetMapping("/{entradaId}")
    public ResponseEntity<EntradaDto> obtenerEntrada(
            @PathVariable String entradaId,
            Authentication auth) {

        JwtUser caller = JwtUser.from(auth);
        EntradaDto entrada = service.obtenerEntrada(entradaId, caller.getUserId(), caller.isStaff());
        return ResponseEntity.ok(entrada);
    }

    /**
     * DELETE /v1/entradas/{entradaId}
     * Anula una entrada (solo staff/admin).
     */
    @DeleteMapping("/{entradaId}")
    public ResponseEntity<Void> anularEntrada(
            @PathVariable String entradaId,
            Authentication auth) {

        JwtUser caller = JwtUser.from(auth);
        service.anularEntrada(entradaId, caller.isStaff());
        return ResponseEntity.noContent().build();
    }
}
