package com.asee.estadisticas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record JugadorEstadisticasUpdate(
        @NotNull UUID jugadorId,
        @NotNull @Valid EstadisticaJugadorPartidoUpdate estadisticas
) {
}
