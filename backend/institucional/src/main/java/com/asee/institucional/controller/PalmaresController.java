package com.asee.institucional.controller;

import com.asee.institucional.dto.TituloCreate;
import com.asee.institucional.dto.TituloResponse;
import com.asee.institucional.service.TituloService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/club/palmares")
public class PalmaresController {

    private final TituloService service;

    public PalmaresController(TituloService service) {
        this.service = service;
    }

    @GetMapping
    public List<TituloResponse> listar() {
        return service.listar().stream()
                .map(TituloResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TituloResponse crear(@Valid @RequestBody TituloCreate dto) {
        return TituloResponse.from(service.crear(dto));
    }

    @PutMapping("/{tituloId}")
    public TituloResponse actualizar(@PathVariable("tituloId") UUID tituloId,
                                     @Valid @RequestBody TituloCreate dto) {
        return TituloResponse.from(service.actualizar(tituloId, dto));
    }

    @DeleteMapping("/{tituloId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("tituloId") UUID tituloId) {
        service.eliminar(tituloId);
    }
}