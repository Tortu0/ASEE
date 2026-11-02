package com.asee.deportivo.controller;

import com.asee.deportivo.dto.PartidoCreate;
import com.asee.deportivo.dto.PartidoResponse;
import com.asee.deportivo.model.EstadoPartido;
import com.asee.deportivo.model.Partido;
import com.asee.deportivo.service.PartidoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/partidos")
public class PartidoController {

    private final PartidoService service;

    public PartidoController(PartidoService service) {
        this.service = service;
    }

    @GetMapping
    public List<PartidoResponse> listar(
            @RequestParam(name = "estado", required = false) EstadoPartido estado,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(name = "ventaAbierta", required = false) Boolean ventaAbierta,
            @RequestParam(name = "pagina", defaultValue = "1") @Min(1) int pagina,
            @RequestParam(name = "tamano", defaultValue = "20") @Min(1) @Max(100) int tamano) {

        return service.listar(estado, desde, hasta, ventaAbierta, pagina, tamano)
                .stream()
                .map(PartidoResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<PartidoResponse> crear(@Valid @RequestBody PartidoCreate dto) {
        Partido creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.partidoId())
                .toUri();
        return ResponseEntity.created(location).body(PartidoResponse.from(creado));
    }

    @GetMapping("/{partidoId}")
    public PartidoResponse obtener(@PathVariable("partidoId") UUID partidoId) {
        return PartidoResponse.from(service.obtener(partidoId));
    }

    @PutMapping("/{partidoId}")
    public PartidoResponse actualizar(@PathVariable("partidoId") UUID partidoId,
                                      @Valid @RequestBody PartidoCreate dto) {
        return PartidoResponse.from(service.actualizar(partidoId, dto));
    }

    @DeleteMapping("/{partidoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("partidoId") UUID partidoId) {
        service.eliminar(partidoId);
    }
}