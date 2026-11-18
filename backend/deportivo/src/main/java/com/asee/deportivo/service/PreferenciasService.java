package com.asee.deportivo.service;

import com.asee.deportivo.dto.PreferenciasDto;
import com.asee.deportivo.exception.PeticionInvalidaException;
import com.asee.deportivo.exception.RecursoNoEncontradoException;
import com.asee.deportivo.model.PreferenciasNotificacion;
import com.asee.deportivo.repository.PreferenciasRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PreferenciasService {

    private final PreferenciasRepository repository;

    public PreferenciasService(PreferenciasRepository repository) {
        this.repository = repository;
    }

    public PreferenciasNotificacion obtener(UUID usuarioId) {
        return repository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "El usuario " + usuarioId + " no tiene preferencias de notificación configuradas"));
    }

    public PreferenciasNotificacion actualizar(UUID usuarioId, PreferenciasDto dto) {
        if (dto.usuarioId() != null && !dto.usuarioId().equals(usuarioId)) {
            throw new PeticionInvalidaException(
                    "El usuarioId del cuerpo no coincide con el de la ruta");
        }
        PreferenciasNotificacion preferencias = new PreferenciasNotificacion(
                usuarioId,
                dto.jugadoresFavoritos() != null ? dto.jugadoresFavoritos() : List.of(),
                dto.categoriasFavoritas() != null ? dto.categoriasFavoritas() : List.of(),
                dto.avisarInicioPartido() == null || dto.avisarInicioPartido());
        return repository.save(preferencias);
    }
}