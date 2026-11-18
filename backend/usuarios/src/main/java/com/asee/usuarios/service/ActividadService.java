package com.asee.usuarios.service;

import com.asee.usuarios.dto.ActividadCreateDTO;
import com.asee.usuarios.dto.ActividadResponseDTO;
import com.asee.usuarios.entity.Actividad;
import com.asee.usuarios.repository.ActividadRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
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
        return actividades.stream().map(ActividadService::aDto).collect(Collectors.toList());
    }

    /**
     * POST /usuarios/{id}/historial (solo admin): alta manual de una actividad.
     * IllegalArgumentException -> 404 (usuario inexistente); IllegalStateException -> 409 (ya existe).
     */
    public ActividadResponseDTO registrarManual(UUID usuarioId, ActividadCreateDTO dto, LocalDateTime fecha) {
        if (!usuarioRepository.existsById(usuarioId)) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        if (actividadRepository.existsByUsuarioIdAndTipoAndReferenciaId(usuarioId, dto.getTipo(), dto.getReferenciaId())) {
            throw new IllegalStateException("La actividad ya existe en el historial");
        }
        Actividad actividad = new Actividad();
        actividad.setUsuarioId(usuarioId);
        actividad.setTipo(dto.getTipo());
        actividad.setReferenciaId(dto.getReferenciaId());
        actividad.setImporte(dto.getImporte());
        actividad.setDescripcion(dto.getDescripcion());
        actividad.setFecha(fecha);
        return aDto(actividadRepository.save(actividad));
    }

    /** Convierte un texto ISO-8601 (con zona, p. ej. "...Z", o sin ella) a hora local; null si no es válido. */
    public static LocalDateTime parseFecha(String texto) {
        if (texto == null || texto.isBlank()) return null;
        try {
            return OffsetDateTime.parse(texto).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(texto);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private static ActividadResponseDTO aDto(Actividad act) {
        ActividadResponseDTO dto = new ActividadResponseDTO();
        dto.setTipo(act.getTipo());
        dto.setReferenciaId(act.getReferenciaId());
        dto.setDescripcion(act.getDescripcion());
        dto.setImporte(act.getImporte());
        dto.setFecha(act.getFecha());
        return dto;
    }
}
