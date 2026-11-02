package com.asee.usuarios.dto;

import java.time.LocalDateTime;

public class ActividadResponseDTO {
    private String tipo;
    private String referenciaId;
    private LocalDateTime fecha;

    public ActividadResponseDTO() {}

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getReferenciaId() { return referenciaId; }
    public void setReferenciaId(String referenciaId) { this.referenciaId = referenciaId; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}