package com.asee.usuarios.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ActividadResponseDTO {

    private String tipo;
    private String referenciaId;
    private String descripcion;
    private BigDecimal importe;
    private LocalDateTime fecha;

    public ActividadResponseDTO() {}

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getReferenciaId() { return referenciaId; }
    public void setReferenciaId(String referenciaId) { this.referenciaId = referenciaId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
