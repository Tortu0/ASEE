package com.asee.institucional.service;

import com.asee.institucional.dto.InstalacionCreate;
import com.asee.institucional.model.Instalacion;
import com.asee.institucional.repository.InstalacionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class InstalacionService {

    private final InstalacionRepository repository;

    public InstalacionService(InstalacionRepository repository) {
        this.repository = repository;
    }

    public List<Instalacion> listar() {
        return repository.findAll(Sort.by("nombre").ascending());
    }

    public Instalacion crear(InstalacionCreate dto) {
        Instalacion nueva = new Instalacion(
                UUID.randomUUID(),
                dto.nombre(),
                dto.direccion(),
                dto.comoLlegar(),
                dto.capacidad(),
                dto.fotos() != null ? dto.fotos() : List.of(),
                dto.latitud(),
                dto.longitud());
        return repository.save(nueva);
    }
}