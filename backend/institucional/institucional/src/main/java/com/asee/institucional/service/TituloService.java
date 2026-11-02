package com.asee.institucional.service;

import com.asee.institucional.dto.TituloCreate;
import com.asee.institucional.exception.RecursoNoEncontradoException;
import com.asee.institucional.model.Titulo;
import com.asee.institucional.repository.TituloRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TituloService {

    private final TituloRepository repository;

    public TituloService(TituloRepository repository) {
        this.repository = repository;
    }

    public List<Titulo> listar() {
        // De la temporada más reciente a la más antigua
        return repository.findAll(Sort.by("temporada").descending());
    }

    public Titulo crear(TituloCreate dto) {
        Titulo nuevo = new Titulo(
                UUID.randomUUID(),
                dto.competicion(),
                dto.temporada(),
                dto.descripcion(),
                dto.fotoUrl());
        return repository.save(nuevo);
    }

    public Titulo actualizar(UUID id, TituloCreate dto) {
        comprobarExiste(id);
        Titulo actualizado = new Titulo(
                id,
                dto.competicion(),
                dto.temporada(),
                dto.descripcion(),
                dto.fotoUrl());
        return repository.save(actualizado);
    }

    public void eliminar(UUID id) {
        comprobarExiste(id);
        repository.deleteById(id);
    }

    private void comprobarExiste(UUID id) {
        if (!repository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "No existe ningún título con el identificador " + id);
        }
    }
}