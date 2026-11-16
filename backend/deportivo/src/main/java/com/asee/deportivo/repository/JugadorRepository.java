package com.asee.deportivo.repository;

import com.asee.deportivo.model.Jugador;
import com.asee.deportivo.model.Posicion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JugadorRepository extends MongoRepository<Jugador, UUID> {

    List<Jugador> findByActivo(boolean activo, Pageable pageable);

    List<Jugador> findByActivoAndPosicion(boolean activo, Posicion posicion, Pageable pageable);

    // Para comprobar que no hay dos jugadores activos con el mismo dorsal
    Optional<Jugador> findByDorsalAndActivoTrue(int dorsal);
}