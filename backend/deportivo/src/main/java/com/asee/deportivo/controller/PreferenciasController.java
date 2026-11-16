package com.asee.deportivo.controller;

import com.asee.deportivo.dto.PreferenciasDto;
import com.asee.deportivo.service.PreferenciasService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/usuarios/{usuarioId}/preferencias-notificacion")
public class PreferenciasController {

    private final PreferenciasService service;

    public PreferenciasController(PreferenciasService service) {
        this.service = service;
    }

    @GetMapping
    public PreferenciasDto obtener(@PathVariable("usuarioId") UUID usuarioId) {
        return PreferenciasDto.from(service.obtener(usuarioId));
    }

    @PutMapping
    public PreferenciasDto actualizar(@PathVariable("usuarioId") UUID usuarioId,
                                      @RequestBody PreferenciasDto dto) {
        return PreferenciasDto.from(service.actualizar(usuarioId, dto));
    }
}