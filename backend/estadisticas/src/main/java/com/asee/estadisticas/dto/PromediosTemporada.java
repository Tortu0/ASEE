package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema PromediosTemporada del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromediosTemporada(
        Double minutosPorPartido,
        Double puntosPorPartido,
        Double rebotesPorPartido,
        Double asistenciasPorPartido,
        Double robosPorPartido,
        Double perdidasPorPartido,
        Double taponesPorPartido,
        Double faltasPorPartido,
        Double masMenosPorPartido,
        Double valoracionPorPartido
) {
}
