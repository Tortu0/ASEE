package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstadisticasPartidoUpdate {
    @Min(1) @Max(10)
    private Integer periodoActual;
    private boolean periodoActualPresente;
    @Min(0) @Max(600)
    private Integer segundosRestantesPeriodo;
    private boolean segundosRestantesPeriodoPresente;
    @NotNull @Size(min = 1)
    private List<@NotNull @Valid JugadorEstadisticasUpdate> jugadores;

    @JsonIgnore
    public boolean tienePeriodoActual() { return periodoActualPresente; }

    public Integer getPeriodoActual() { return periodoActual; }

    @JsonSetter
    public void setPeriodoActual(Integer periodoActual) { this.periodoActual = periodoActual; this.periodoActualPresente = true; }

    @JsonIgnore
    public boolean tieneSegundosRestantesPeriodo() { return segundosRestantesPeriodoPresente; }

    public Integer getSegundosRestantesPeriodo() { return segundosRestantesPeriodo; }

    @JsonSetter
    public void setSegundosRestantesPeriodo(Integer segundosRestantesPeriodo) { this.segundosRestantesPeriodo = segundosRestantesPeriodo; this.segundosRestantesPeriodoPresente = true; }

    public List<@NotNull @Valid JugadorEstadisticasUpdate> getJugadores() { return jugadores; }

    @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
    public void setJugadores(List<@NotNull @Valid JugadorEstadisticasUpdate> jugadores) { this.jugadores = jugadores; }

    @JsonIgnore
    @AssertTrue(message = "Un jugador no puede aparecer dos veces")
    public boolean isJugadoresUnicos() {
        if (jugadores == null) return true;
        var ids = jugadores.stream().filter(java.util.Objects::nonNull)
                .map(JugadorEstadisticasUpdate::jugadorId).filter(java.util.Objects::nonNull).toList();
        return ids.stream().distinct().count() == ids.size();
    }
}
