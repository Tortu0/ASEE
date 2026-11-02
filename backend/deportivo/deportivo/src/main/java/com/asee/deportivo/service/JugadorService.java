package com.asee.deportivo.service;

import com.asee.deportivo.dto.JugadorCreate;
import com.asee.deportivo.exception.ConflictoException;
import com.asee.deportivo.exception.RecursoNoEncontradoException;
import com.asee.deportivo.model.Jugador;
import com.asee.deportivo.model.Posicion;
import com.asee.deportivo.repository.JugadorRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class JugadorService {

    private final JugadorRepository repository;

    public JugadorService(JugadorRepository repository) {
        this.repository = repository;
    }

    public List<Jugador> listar(Posicion posicion, boolean activo, int pagina, int tamano) {
        Pageable pageable = PageRequest.of(pagina - 1, tamano, Sort.by("dorsal").ascending());
        return posicion == null
                ? repository.findByActivo(activo, pageable)
                : repository.findByActivoAndPosicion(activo, posicion, pageable);
    }

    public Jugador obtener(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ningún jugador con el identificador " + id));
    }

    public Jugador crear(JugadorCreate dto) {
        boolean activo = dto.activo() == null || dto.activo();
        if (activo) {
            comprobarDorsalLibre(dto.dorsal(), null);
        }
        Jugador nuevo = new Jugador(
                UUID.randomUUID(),
                dto.nombre(),
                dto.dorsal(),
                dto.posicion(),
                dto.alturaCm(),
                dto.fechaNacimiento(),
                dto.fotoUrl(),
                activo);
        return repository.save(nuevo);
    }

    public Jugador actualizar(UUID id, JugadorCreate dto) {
        Jugador actual = obtener(id);
        boolean activo = dto.activo() != null ? dto.activo() : actual.activo();
        if (activo) {
            comprobarDorsalLibre(dto.dorsal(), id);
        }
        Jugador actualizado = new Jugador(
                id,
                dto.nombre(),
                dto.dorsal(),
                dto.posicion(),
                dto.alturaCm(),
                dto.fechaNacimiento(),
                dto.fotoUrl(),
                activo);
        return repository.save(actualizado);
    }

    public void eliminar(UUID id) {
        if (!repository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "No existe ningún jugador con el identificador " + id);
        }
        repository.deleteById(id);
    }

    // Lanza 409 si otro jugador activo (distinto de idPropio) ya lleva ese dorsal
    private void comprobarDorsalLibre(int dorsal, UUID idPropio) {
        repository.findByDorsalAndActivoTrue(dorsal)
                .filter(otro -> !otro.jugadorId().equals(idPropio))
                .ifPresent(otro -> {
                    throw new ConflictoException(
                            "El dorsal " + dorsal + " ya está asignado a otro jugador activo");
                });
    }
}