package com.asee.institucional.controller;

import com.asee.institucional.dto.HistoriaDto;
import com.asee.institucional.dto.IdentidadDto;
import com.asee.institucional.dto.MiembroJuntaDto;
import com.asee.institucional.service.ClubService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/club")
public class ClubController {

    private final ClubService service;

    public ClubController(ClubService service) {
        this.service = service;
    }

    // ---------- Historia ----------

    @GetMapping("/historia")
    public HistoriaDto obtenerHistoria() {
        return HistoriaDto.from(service.obtenerHistoria());
    }

    @PutMapping("/historia")
    public HistoriaDto actualizarHistoria(@Valid @RequestBody HistoriaDto dto) {
        return HistoriaDto.from(service.actualizarHistoria(dto));
    }

    // ---------- Identidad ----------

    @GetMapping("/identidad")
    public IdentidadDto obtenerIdentidad() {
        return IdentidadDto.from(service.obtenerIdentidad());
    }

    @PutMapping("/identidad")
    public IdentidadDto actualizarIdentidad(@Valid @RequestBody IdentidadDto dto) {
        return IdentidadDto.from(service.actualizarIdentidad(dto));
    }

    // ---------- Junta directiva ----------

    @GetMapping("/junta")
    public List<MiembroJuntaDto> listarJunta() {
        return service.obtenerJunta().stream()
                .map(MiembroJuntaDto::from)
                .toList();
    }

    // Recibe un array JSON y sustituye la composición entera.
    // Sin @Valid a propósito: nombre y cargo se comprueban en el servicio.
    @PutMapping("/junta")
    public List<MiembroJuntaDto> actualizarJunta(@RequestBody List<MiembroJuntaDto> miembros) {
        return service.actualizarJunta(miembros).stream()
                .map(MiembroJuntaDto::from)
                .toList();
    }
}