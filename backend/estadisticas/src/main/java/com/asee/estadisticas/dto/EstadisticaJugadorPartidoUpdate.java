package com.asee.estadisticas.dto;

import com.asee.estadisticas.model.*;
import com.fasterxml.jackson.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EstadisticaJugadorPartidoUpdate {
    private Boolean titular;
    @Min(0)
    private Integer segundosJugados;
    @Min(0)
    private Integer puntos;
    @Min(0)
    private Integer tiros2Anotados;
    @Min(0)
    private Integer tiros2Intentados;
    @Min(0)
    private Integer tiros3Anotados;
    @Min(0)
    private Integer tiros3Intentados;
    @Min(0)
    private Integer tirosLibresAnotados;
    @Min(0)
    private Integer tirosLibresIntentados;
    @Min(0)
    private Integer rebotesOfensivos;
    @Min(0)
    private Integer rebotesDefensivos;
    @Min(0)
    private Integer asistencias;
    @Min(0)
    private Integer robos;
    @Min(0)
    private Integer perdidas;
    @Min(0)
    private Integer tapones;
    @Min(0)
    private Integer taponesRecibidos;
    @Min(0) @Max(5)
    private Integer faltasCometidas;
    @Min(0)
    private Integer faltasRecibidas;
    private Integer masMenos;

    public Boolean getTitular() { return titular; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitular(Boolean titular) { this.titular = titular; }

    public Integer getSegundosJugados() { return segundosJugados; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setSegundosJugados(Integer segundosJugados) { this.segundosJugados = segundosJugados; }

    public Integer getPuntos() { return puntos; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setPuntos(Integer puntos) { this.puntos = puntos; }

    public Integer getTiros2Anotados() { return tiros2Anotados; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTiros2Anotados(Integer tiros2Anotados) { this.tiros2Anotados = tiros2Anotados; }

    public Integer getTiros2Intentados() { return tiros2Intentados; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTiros2Intentados(Integer tiros2Intentados) { this.tiros2Intentados = tiros2Intentados; }

    public Integer getTiros3Anotados() { return tiros3Anotados; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTiros3Anotados(Integer tiros3Anotados) { this.tiros3Anotados = tiros3Anotados; }

    public Integer getTiros3Intentados() { return tiros3Intentados; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTiros3Intentados(Integer tiros3Intentados) { this.tiros3Intentados = tiros3Intentados; }

    public Integer getTirosLibresAnotados() { return tirosLibresAnotados; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTirosLibresAnotados(Integer tirosLibresAnotados) { this.tirosLibresAnotados = tirosLibresAnotados; }

    public Integer getTirosLibresIntentados() { return tirosLibresIntentados; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTirosLibresIntentados(Integer tirosLibresIntentados) { this.tirosLibresIntentados = tirosLibresIntentados; }

    public Integer getRebotesOfensivos() { return rebotesOfensivos; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRebotesOfensivos(Integer rebotesOfensivos) { this.rebotesOfensivos = rebotesOfensivos; }

    public Integer getRebotesDefensivos() { return rebotesDefensivos; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRebotesDefensivos(Integer rebotesDefensivos) { this.rebotesDefensivos = rebotesDefensivos; }

    public Integer getAsistencias() { return asistencias; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setAsistencias(Integer asistencias) { this.asistencias = asistencias; }

    public Integer getRobos() { return robos; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRobos(Integer robos) { this.robos = robos; }

    public Integer getPerdidas() { return perdidas; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setPerdidas(Integer perdidas) { this.perdidas = perdidas; }

    public Integer getTapones() { return tapones; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTapones(Integer tapones) { this.tapones = tapones; }

    public Integer getTaponesRecibidos() { return taponesRecibidos; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setTaponesRecibidos(Integer taponesRecibidos) { this.taponesRecibidos = taponesRecibidos; }

    public Integer getFaltasCometidas() { return faltasCometidas; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setFaltasCometidas(Integer faltasCometidas) { this.faltasCometidas = faltasCometidas; }

    public Integer getFaltasRecibidas() { return faltasRecibidas; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setFaltasRecibidas(Integer faltasRecibidas) { this.faltasRecibidas = faltasRecibidas; }

    public Integer getMasMenos() { return masMenos; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setMasMenos(Integer masMenos) { this.masMenos = masMenos; }

    @JsonIgnore
    @AssertTrue(message = "Debe enviarse al menos un campo editable")
    public boolean isContenidoValido() {
        return titular != null
                || segundosJugados != null
                || puntos != null
                || tiros2Anotados != null
                || tiros2Intentados != null
                || tiros3Anotados != null
                || tiros3Intentados != null
                || tirosLibresAnotados != null
                || tirosLibresIntentados != null
                || rebotesOfensivos != null
                || rebotesDefensivos != null
                || asistencias != null
                || robos != null
                || perdidas != null
                || tapones != null
                || taponesRecibidos != null
                || faltasCometidas != null
                || faltasRecibidas != null
                || masMenos != null;
    }

    @JsonAnySetter
    public void comprobarPropiedadAdicional(String nombre, Object valor) {
        switch (nombre) {
            case "rebotesTotales", "valoracion", "tirosCampoAnotados", "tirosCampoIntentados",
                    "porcentajes", "metricasAvanzadas" ->
                throw new IllegalArgumentException("Campo calculado no editable: " + nombre);
            default -> { /* OpenAPI no prohíbe propiedades adicionales. */ }
        }
    }
}
