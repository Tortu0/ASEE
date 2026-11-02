package com.asee.entradas.repository;

import com.asee.entradas.entity.Reserva;
import com.asee.entradas.entity.EstadoReserva;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface ReservaRepository extends MongoRepository<Reserva, String> {

    List<Reserva> findByEstadoAndExpiraEnBefore(EstadoReserva estado, Instant now);

    List<Reserva> findByUsuarioId(String usuarioId);
}
