package com.asee.institucional.service;

import com.asee.institucional.dto.PatrocinadorCreate;
import com.asee.institucional.exception.RecursoNoEncontradoException;
import com.asee.institucional.model.NivelPatrocinio;
import com.asee.institucional.model.Patrocinador;
import com.asee.institucional.repository.PatrocinadorRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PatrocinadorService {

    private final PatrocinadorRepository repository;

    public PatrocinadorService(PatrocinadorRepository repository) {
        this.repository = repository;
    }

    public List<Patrocinador> listar(NivelPatrocinio nivel) {
        Sort orden = Sort.by("nombre").ascending();
        return nivel == null
                ? repository.findAll(orden)
                : repository.findByNivel(nivel, orden);
    }

    public Patrocinador obtener(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ningún patrocinador con el identificador " + id));
    }

    public Patrocinador crear(PatrocinadorCreate dto) {
        Patrocinador nuevo = new Patrocinador(
                UUID.randomUUID(),
                dto.nombre(),
                dto.logoUrl(),
                dto.enlace(),
                dto.nivel());
        return repository.save(nuevo);
    }

    public Patrocinador actualizar(UUID id, PatrocinadorCreate dto) {
        obtener(id); // lanza 404 si no existe
        Patrocinador actualizado = new Patrocinador(
                id,
                dto.nombre(),
                dto.logoUrl(),
                dto.enlace(),
                dto.nivel());
        return repository.save(actualizado);
    }

    public void eliminar(UUID id) {
        if (!repository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "No existe ningún patrocinador con el identificador " + id);
        }
        repository.deleteById(id);
    }
}