package com.asee.entradas.repository;

import com.asee.entradas.entity.Tarifa;
import com.asee.entradas.entity.TipoEntrada;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface TarifaRepository extends MongoRepository<Tarifa, String> {

    List<Tarifa> findByPartidoId(String partidoId);

    Optional<Tarifa> findByPartidoIdAndZonaAndTipoEntrada(String partidoId, String zona, TipoEntrada tipoEntrada);
}
