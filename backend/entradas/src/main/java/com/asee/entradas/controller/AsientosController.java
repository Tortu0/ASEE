package com.asee.entradas.controller;

import com.asee.entradas.dto.AforoDto;
import com.asee.entradas.dto.AsientoDto;
import com.asee.entradas.dto.PrecioDto;
import com.asee.entradas.entity.EstadoAsiento;
import com.asee.entradas.service.EntradasService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de asientos: mapa, aforo y precios.
 * Todos son públicos (no requieren autenticación).
 */
@RestController
@RequestMapping("/partidos/{partidoId}")
@RequiredArgsConstructor
public class AsientosController {

    private final EntradasService service;

    /**
     * GET /v1/partidos/{partidoId}/asientos
     * Mapa de asientos de un partido con filtros opcionales.
     */
    @GetMapping("/asientos")
    public ResponseEntity<List<AsientoDto>> listarAsientos(
            @PathVariable String partidoId,
            @RequestParam(required = false) String zona,
            @RequestParam(required = false) EstadoAsiento estado) {

        return ResponseEntity.ok(service.listarAsientos(partidoId, zona, estado));
    }

    /**
     * GET /v1/partidos/{partidoId}/aforo
     * Aforo total y ocupación actual.
     */
    @GetMapping("/aforo")
    public ResponseEntity<AforoDto> obtenerAforo(@PathVariable String partidoId) {
        return ResponseEntity.ok(service.obtenerAforo(partidoId));
    }

    /**
     * GET /v1/partidos/{partidoId}/precios
     * Tarifas del partido por zona y tipo de entrada.
     */
    @GetMapping("/precios")
    public ResponseEntity<List<PrecioDto>> listarPrecios(@PathVariable String partidoId) {
        return ResponseEntity.ok(service.listarPrecios(partidoId));
    }
}
