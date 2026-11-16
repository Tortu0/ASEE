package com.asee.usuarios.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class SocioResponseDTO {
    private UUID usuarioId;
    private String numeroSocio;
    private String estado;
    private String tipoCuota;
    private LocalDate fechaAlta;
    private LocalDate fechaCaducidad;
    private BigDecimal descuentoPorcentaje;

    public SocioResponseDTO() {}

    // --- GETTERS Y SETTERS ---
    public UUID getUsuarioId() { return usuarioId; }
    public void setUsuarioId(UUID usuarioId) { this.usuarioId = usuarioId; }
    public String getNumeroSocio() { return numeroSocio; }
    public void setNumeroSocio(String numeroSocio) { this.numeroSocio = numeroSocio; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTipoCuota() { return tipoCuota; }
    public void setTipoCuota(String tipoCuota) { this.tipoCuota = tipoCuota; }
    public LocalDate getFechaAlta() { return fechaAlta; }
    public void setFechaAlta(LocalDate fechaAlta) { this.fechaAlta = fechaAlta; }
    public LocalDate getFechaCaducidad() { return fechaCaducidad; }
    public void setFechaCaducidad(LocalDate fechaCaducidad) { this.fechaCaducidad = fechaCaducidad; }
    public BigDecimal getDescuentoPorcentaje() { return descuentoPorcentaje; }
    public void setDescuentoPorcentaje(BigDecimal descuentoPorcentaje) { this.descuentoPorcentaje = descuentoPorcentaje; }
}