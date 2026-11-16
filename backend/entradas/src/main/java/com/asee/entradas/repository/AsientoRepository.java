package com.asee.entradas.repository;

import com.asee.entradas.entity.Asiento;
import com.asee.entradas.entity.EstadoAsiento;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AsientoRepository extends MongoRepository<Asiento, String> {

    List<Asiento> findByPartidoId(String partidoId);

    List<Asiento> findByPartidoIdAndZona(String partidoId, String zona);

    List<Asiento> findByPartidoIdAndEstado(String partidoId, EstadoAsiento estado);

    List<Asiento> findByPartidoIdAndZonaAndEstado(String partidoId, String zona, EstadoAsiento estado);

    long countByPartidoId(String partidoId);

    long countByPartidoIdAndEstado(String partidoId, EstadoAsiento estado);

    boolean existsByPartidoId(String partidoId);
}
