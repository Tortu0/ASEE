package com.asee.usuarios.dto;

import java.math.BigDecimal;

/** Cuerpo de POST /usuarios/{usuarioId}/historial (esquema Actividad del contrato). */
public class ActividadCreateDTO {

    private String tipo;            // "entrada" | "pedido"
    private String referenciaId;
    private String descripcion;
    private BigDecimal importe;
    private String fecha;           // date-time ISO-8601

    public ActividadCreateDTO() {}

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getReferenciaId() { return referenciaId; }
    public void setReferenciaId(String referenciaId) { this.referenciaId = referenciaId; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
}
