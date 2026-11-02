package com.asee.usuarios.service;

import com.asee.usuarios.dto.ActividadResponseDTO;
import com.asee.usuarios.entity.Actividad;
import com.asee.usuarios.repository.ActividadRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ActividadService {

    private final ActividadRepository actividadRepository;
    private final UsuarioRepository usuarioRepository;

    public ActividadService(ActividadRepository actividadRepository, UsuarioRepository usuarioRepository) {
        this.actividadRepository = actividadRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<ActividadResponseDTO> obtenerHistorialUsuario(UUID usuarioId) {
        // Regla de negocio: Si el usuario no existe, lanzamos error 404
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }

        // Recuperamos la lista de la base de datos ordenada por fecha descendente
        List<Actividad> actividades = actividadRepository.findByUsuarioIdOrderByFechaDesc(usuarioId);

        // Transformamos la lista de entidades a lista de DTOs usando Java Streams
        return actividades.stream().map(act -> {
            ActividadResponseDTO dto = new ActividadResponseDTO();
            dto.setTipo(act.getTipo());
            dto.setReferenciaId(act.getReferenciaId());
            dto.setFecha(act.getFecha());
            return dto;
        }).collect(Collectors.toList());
    }
}