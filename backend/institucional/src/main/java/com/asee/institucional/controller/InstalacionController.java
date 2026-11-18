package com.asee.institucional.controller;

import com.asee.institucional.dto.InstalacionCreate;
import com.asee.institucional.dto.InstalacionResponse;
import com.asee.institucional.service.InstalacionService;
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
@RequestMapping("/club/instalaciones")
public class InstalacionController {

    private final InstalacionService service;

    public InstalacionController(InstalacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<InstalacionResponse> listar() {
        return service.listar().stream()
                .map(InstalacionResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InstalacionResponse crear(@Valid @RequestBody InstalacionCreate dto) {
        return InstalacionResponse.from(service.crear(dto));
    }
}