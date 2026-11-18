package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representación JSON del esquema MetricasAvanzadas del contrato. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MetricasAvanzadas(
        @JsonInclude(JsonInclude.Include.ALWAYS)  Double porcentajeTiroEfectivo,
        @JsonInclude(JsonInclude.Include.ALWAYS)  Double trueShooting,
        @JsonInclude(JsonInclude.Include.ALWAYS)  Double ratioAsistenciasPerdidas
) {
}
