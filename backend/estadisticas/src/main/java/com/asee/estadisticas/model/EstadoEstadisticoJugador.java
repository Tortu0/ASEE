package com.asee.estadisticas.model;

import java.util.UUID;

/** La estadística final se obtiene sumando eventos activos y ajustes vigentes. */
public record EstadoEstadisticoJugador(
        UUID jugadorId,
        ContadoresEstadisticos sumaEventos,
        ContadoresEstadisticos ajustes,
        DatosManualesJugador manuales
) {
}
