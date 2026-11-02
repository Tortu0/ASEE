package com.asee.deportivo.controller;

import com.asee.deportivo.dto.NoticiaCreate;
import com.asee.deportivo.dto.NoticiaResponse;
import com.asee.deportivo.model.CategoriaNoticia;
import com.asee.deportivo.model.Noticia;
import com.asee.deportivo.service.NoticiaService;
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
@RequestMapping("/noticias")
public class NoticiaController {

    private final NoticiaService service;

    public NoticiaController(NoticiaService service) {
        this.service = service;
    }

    @GetMapping
    public List<NoticiaResponse> listar(
            @RequestParam(name = "categoria", required = false) CategoriaNoticia categoria,
            @RequestParam(name = "jugadorId", required = false) UUID jugadorId,
            @RequestParam(name = "pagina", defaultValue = "1") @Min(1) int pagina,
            @RequestParam(name = "tamano", defaultValue = "20") @Min(1) @Max(100) int tamano) {

        return service.listar(categoria, jugadorId, pagina, tamano)
                .stream()
                .map(NoticiaResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<NoticiaResponse> crear(
            @Valid @RequestBody NoticiaCreate dto,
            @RequestHeader(name = "X-User-Id", required = false) UUID autorId) {

        Noticia creada = service.crear(dto, autorId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(creada.noticiaId())
                .toUri();
        return ResponseEntity.created(location).body(NoticiaResponse.from(creada));
    }

    @GetMapping("/{noticiaId}")
    public NoticiaResponse obtener(@PathVariable("noticiaId") UUID noticiaId) {
        return NoticiaResponse.from(service.obtener(noticiaId));
    }

    @PutMapping("/{noticiaId}")
    public NoticiaResponse actualizar(@PathVariable("noticiaId") UUID noticiaId,
                                      @Valid @RequestBody NoticiaCreate dto) {
        return NoticiaResponse.from(service.actualizar(noticiaId, dto));
    }

    @DeleteMapping("/{noticiaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable("noticiaId") UUID noticiaId) {
        service.eliminar(noticiaId);
    }
}