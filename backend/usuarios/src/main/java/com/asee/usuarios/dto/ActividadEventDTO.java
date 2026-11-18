package com.asee.usuarios.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpo de los eventos "entrada.comprada" y "pedido.creado" (memoria 3.4):
 * eventoId, usuarioId, referenciaId, fecha (ISO-8601), importe y descripcion.
 * El tipo de actividad NO viaja en el cuerpo: se deduce de la routing key.
 */
public class ActividadEventDTO {

    private String eventoId;
    private UUID usuarioId;
    private String referenciaId;   // id de la entrada o del pedido en su microservicio
    private String fecha;          // texto ISO-8601 (p. ej. 2026-11-11T10:15:30Z)
    private BigDecimal importe;
    private String descripcion;

    public ActividadEventDTO() {}

    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getReferenciaId() { return referenciaId; }
    public void setReferenciaId(String referenciaId) { this.referenciaId = referenciaId; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public BigDecimal getImporte() { return importe; }
    public void setImporte(BigDecimal importe) { this.importe = importe; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
}
