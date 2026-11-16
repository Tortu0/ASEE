package com.asee.deportivo.controller;

import com.asee.deportivo.dto.JugadorCreate;
import com.asee.deportivo.dto.JugadorResponse;
import com.asee.deportivo.model.Jugador;
import com.asee.deportivo.model.Posicion;
import com.asee.deportivo.service.JugadorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/jugadores")
public class JugadorController {

    private final JugadorService service;

    public JugadorController(JugadorService service) {
        this.service = service;
    }

    @GetMapping
    public List<JugadorResponse> listar(
            @RequestParam(name = "posicion", required = false) Posicion posicion,
            @RequestParam(name = "activo", defaultValue = "true") boolean activo,
            @RequestParam(name = "pagina", defaultValue = "1") @Min(1) int pagina,
            @RequestParam(name = "tamano", defaultValue = "20") @Min(1) @Max(100) int tamano) {

        return service.listar(posicion, activo, pagina, tamano)
                .stream()
                .map(JugadorResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<JugadorResponse> crear(@Valid @RequestBody JugadorCreate dto) {
        Jugador creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.jugadorId())
                .toUri();
        return ResponseEntity.created(location).body(JugadorResponse.from(creado));
    }

    @GetMapping("/{jugadorId}")
    public JugadorResponse obtener(@PathVariable("jugadorId") UUID jugadorId) {
        return JugadorResponse.from(service.obtener(jugadorId));
    }

    @PutMapping("/{jugadorId}")
    public JugadorResponse actualizar(@PathVariable("jugadorId") UUID jugadorId,
                                      @Valid @RequestBody JugadorCreate dto) {
        return JugadorResponse.from(service.actualizar(jugadorId, dto));
    }

    @DeleteMapping("/{jugadorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("jugadorId") UUID jugadorId) {
        service.eliminar(jugadorId);
    }
}