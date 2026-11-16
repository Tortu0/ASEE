package com.asee.deportivo.service;

import com.asee.deportivo.dto.PartidoCreate;
import com.asee.deportivo.exception.PeticionInvalidaException;
import com.asee.deportivo.exception.RecursoNoEncontradoException;
import com.asee.deportivo.model.EstadoPartido;
import com.asee.deportivo.model.Partido;
import com.asee.deportivo.repository.PartidoRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class PartidoService {

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    private final PartidoRepository repository;

    public PartidoService(PartidoRepository repository) {
        this.repository = repository;
    }

    public List<Partido> listar(EstadoPartido estado, LocalDate desde, LocalDate hasta,
                                Boolean ventaAbierta, int pagina, int tamano) {
        // El YAML dice que 'hasta' es inclusivo: se busca hasta el inicio del día siguiente
        Instant desdeInstant = desde == null ? null : desde.atStartOfDay(ZONA).toInstant();
        Instant hastaInstant = hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
        return repository.buscar(estado, desdeInstant, hastaInstant, ventaAbierta, pagina, tamano);
    }

    public Partido obtener(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ningún partido con el identificador " + id));
    }

    public Partido crear(PartidoCreate dto) {
        Partido nuevo = new Partido(
                UUID.randomUUID(),
                dto.fechaHora(),
                dto.rival(),
                dto.lugar(),
                dto.esLocal(),
                dto.temporada(),
                dto.competicion(),
                dto.jornada(),
                dto.estado() != null ? dto.estado() : EstadoPartido.programado,
                dto.puntosLocal(),
                dto.puntosVisitante(),
                dto.ventaEntradasAbierta() != null && dto.ventaEntradasAbierta());
        return repository.save(nuevo);
    }

    public Partido actualizar(UUID id, PartidoCreate dto) {
        Partido actual = obtener(id);

        if (!actual.temporada().equals(dto.temporada())) {
            throw new PeticionInvalidaException(
                    "La temporada no se puede modificar: el partido pertenece a " + actual.temporada());
        }

        Partido actualizado = new Partido(
                id,
                dto.fechaHora(),
                dto.rival(),
                dto.lugar(),
                dto.esLocal(),
                actual.temporada(),
                dto.competicion(),
                dto.jornada(),
                dto.estado() != null ? dto.estado() : actual.estado(),
                dto.puntosLocal(),
                dto.puntosVisitante(),
                dto.ventaEntradasAbierta() != null
                        ? dto.ventaEntradasAbierta()
                        : actual.ventaEntradasAbierta());
        return repository.save(actualizado);
    }

    public void eliminar(UUID id) {
        if (!repository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "No existe ningún partido con el identificador " + id);
        }
        repository.deleteById(id);
    }
}