package com.asee.institucional.service;

import com.asee.institucional.dto.CategoriaCanteraCreate;
import com.asee.institucional.model.CategoriaCantera;
import com.asee.institucional.repository.CategoriaCanteraRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CanteraService {

    private final CategoriaCanteraRepository repository;

    public CanteraService(CategoriaCanteraRepository repository) {
        this.repository = repository;
    }

    public List<CategoriaCantera> listar() {
        return repository.findAll(Sort.by("nombre").ascending());
    }

    public CategoriaCantera crear(CategoriaCanteraCreate dto) {
        CategoriaCantera nueva = new CategoriaCantera(
                UUID.randomUUID(),
                dto.nombre(),
                dto.edades(),
                dto.entrenador(),
                dto.horarios());
        return repository.save(nueva);
    }
}