package com.asee.entradas.controller;

import com.asee.entradas.dto.ReservaCreate;
import com.asee.entradas.dto.ReservaDto;
import com.asee.entradas.security.JwtUser;
import com.asee.entradas.service.EntradasService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de reservas: crear, consultar y cancelar.
 * Todos requieren autenticación JWT.
 */
@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservasController {

    private final EntradasService service;

    /**
     * POST /v1/reservas
     * Crea una reserva temporal bloqueando los asientos indicados.
     */
    @PostMapping
    public ResponseEntity<ReservaDto> crearReserva(
            @Valid @RequestBody ReservaCreate req,
            Authentication auth) {

        JwtUser caller = JwtUser.from(auth);
        ReservaDto reserva = service.crearReserva(req, caller.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(reserva);
    }

    /**
     * GET /v1/reservas/{reservaId}
     * Consulta una reserva existente.
     */
    @GetMapping("/{reservaId}")
    public ResponseEntity<ReservaDto> obtenerReserva(
            @PathVariable String reservaId,
            Authentication auth) {

        JwtUser caller = JwtUser.from(auth);
        ReservaDto reserva = service.obtenerReserva(reservaId, caller.getUserId(), caller.isStaff());
        return ResponseEntity.ok(reserva);
    }

    /**
     * DELETE /v1/reservas/{reservaId}
     * Cancela una reserva y libera los asientos.
     */
    @DeleteMapping("/{reservaId}")
    public ResponseEntity<Void> cancelarReserva(
            @PathVariable String reservaId,
            Authentication auth) {

        JwtUser caller = JwtUser.from(auth);
        service.cancelarReserva(reservaId, caller.getUserId(), caller.isStaff());
        return ResponseEntity.noContent().build();
    }
}
