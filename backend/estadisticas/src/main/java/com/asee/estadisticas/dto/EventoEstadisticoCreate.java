package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventoEstadisticoCreate {
    @NotNull @Size(max = 100)
    private String clientEventoId;
    @NotNull
    private TipoEventoEstadistico tipo;
    private UUID jugadorId;
    private UUID jugadorRelacionadoId;
    @Size(max = 100)
    private String jugadaId;
    @NotNull @Min(1) @Max(10)
    private Integer periodo;
    @Min(0) @Max(600)
    private Integer segundosRestantes;
    private TipoFalta tipoFalta;
    @Size(max = 500)
    private String observaciones;

    public String getClientEventoId() { return clientEventoId; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setClientEventoId(String clientEventoId) { this.clientEventoId = clientEventoId; }

    public TipoEventoEstadistico getTipo() { return tipo; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTipo(TipoEventoEstadistico tipo) { this.tipo = tipo; }

    public UUID getJugadorId() { return jugadorId; }

    @JsonSetter
    public void setJugadorId(UUID jugadorId) { this.jugadorId = jugadorId; }

    public UUID getJugadorRelacionadoId() { return jugadorRelacionadoId; }

    @JsonSetter
    public void setJugadorRelacionadoId(UUID jugadorRelacionadoId) { this.jugadorRelacionadoId = jugadorRelacionadoId; }

    public String getJugadaId() { return jugadaId; }

    @JsonSetter
    public void setJugadaId(String jugadaId) { this.jugadaId = jugadaId; }

    public Integer getPeriodo() { return periodo; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setPeriodo(Integer periodo) { this.periodo = periodo; }

    public Integer getSegundosRestantes() { return segundosRestantes; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setSegundosRestantes(Integer segundosRestantes) { this.segundosRestantes = segundosRestantes; }

    public TipoFalta getTipoFalta() { return tipoFalta; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTipoFalta(TipoFalta tipoFalta) { this.tipoFalta = tipoFalta; }

    public String getObservaciones() { return observaciones; }

    @JsonSetter
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    @JsonIgnore
    @AssertTrue(message = "jugadorId es obligatorio en eventos individuales")
    public boolean isJugadorValido() {
        return tipo == null || tipo.esGeneral() || jugadorId != null;
    }
}
