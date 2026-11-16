package com.asee.institucional.controller;

import com.asee.institucional.dto.CategoriaCanteraCreate;
import com.asee.institucional.dto.CategoriaCanteraResponse;
import com.asee.institucional.service.CanteraService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/club/cantera")
public class CanteraController {

    private final CanteraService service;

    public CanteraController(CanteraService service) {
        this.service = service;
    }

    @GetMapping
    public List<CategoriaCanteraResponse> listar() {
        return service.listar().stream()
                .map(CategoriaCanteraResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaCanteraResponse crear(@Valid @RequestBody CategoriaCanteraCreate dto) {
        return CategoriaCanteraResponse.from(service.crear(dto));
    }
}