package com.asee.deportivo.repository;

import com.asee.deportivo.model.EstadoPartido;
import com.asee.deportivo.model.Partido;

import java.time.Instant;
import java.util.List;

public interface PartidoRepositoryCustom {

    // Todos los filtros son opcionales (null = no filtrar).
    // 'desde' es inclusivo y 'hasta' es exclusivo.
    List<Partido> buscar(EstadoPartido estado,
                         Instant desde,
                         Instant hasta,
                         Boolean ventaAbierta,
                         int pagina,
                         int tamano);
}