package com.asee.usuarios.dto;

import java.util.UUID;

public class ActividadEventDTO {
    private UUID usuarioId;
    private String tipo; // ej: "COMPRA_ENTRADA"
    private String referenciaId; // ej: "TICKET-999"

    public ActividadEventDTO() {}

    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getReferenciaId() { return referenciaId; }
    public void setReferenciaId(String referenciaId) { this.referenciaId = referenciaId; }
}