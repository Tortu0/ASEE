package com.asee.entradas.repository;

import com.asee.entradas.entity.Entrada;
import com.asee.entradas.entity.EstadoEntrada;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EntradaRepository extends MongoRepository<Entrada, String> {

    List<Entrada> findByUsuarioId(String usuarioId);

    List<Entrada> findByUsuarioIdAndPartidoId(String usuarioId, String partidoId);

    boolean existsByReservaId(String reservaId);

    List<Entrada> findByReservaId(String reservaId);

    /** Permite reanudar una emisión interrumpida sin duplicar entradas. */
    Optional<Entrada> findByReservaIdAndAsientoId(String reservaId, String asientoId);

    /** Entradas cuyo evento "entrada comprada" aún no se ha publicado (excluye las anuladas). */
    List<Entrada> findByEventoPublicadoFalseAndEstadoNot(EstadoEntrada estado);
}