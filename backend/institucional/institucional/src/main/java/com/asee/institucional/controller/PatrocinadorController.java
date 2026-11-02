package com.asee.institucional.controller;

import com.asee.institucional.dto.PatrocinadorCreate;
import com.asee.institucional.dto.PatrocinadorResponse;
import com.asee.institucional.model.NivelPatrocinio;
import com.asee.institucional.model.Patrocinador;
import com.asee.institucional.service.PatrocinadorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patrocinadores")
public class PatrocinadorController {

    private final PatrocinadorService service;

    public PatrocinadorController(PatrocinadorService service) {
        this.service = service;
    }

    @GetMapping
    public List<PatrocinadorResponse> listar(
            @RequestParam(name = "nivel", required = false) NivelPatrocinio nivel) {
        return service.listar(nivel).stream()
                .map(PatrocinadorResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<PatrocinadorResponse> crear(@Valid @RequestBody PatrocinadorCreate dto) {
        Patrocinador creado = service.crear(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creado.patrocinadorId())
                .toUri();
        return ResponseEntity.created(location).body(PatrocinadorResponse.from(creado));
    }

    @GetMapping("/{patrocinadorId}")
    public PatrocinadorResponse obtener(@PathVariable("patrocinadorId") UUID patrocinadorId) {
        return PatrocinadorResponse.from(service.obtener(patrocinadorId));
    }

    @PutMapping("/{patrocinadorId}")
    public PatrocinadorResponse actualizar(@PathVariable("patrocinadorId") UUID patrocinadorId,
                                           @Valid @RequestBody PatrocinadorCreate dto) {
        return PatrocinadorResponse.from(service.actualizar(patrocinadorId, dto));
    }

    @DeleteMapping("/{patrocinadorId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("patrocinadorId") UUID patrocinadorId) {
        service.eliminar(patrocinadorId);
    }
}