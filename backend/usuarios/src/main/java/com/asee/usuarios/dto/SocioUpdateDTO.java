package com.asee.usuarios.dto;

import java.time.LocalDate;

public class SocioUpdateDTO {
    private String tipoCuota;
    private LocalDate fechaCaducidad;

    public SocioUpdateDTO() {}

    // --- GETTERS Y SETTERS ---
    public String getTipoCuota() { return tipoCuota; }
    public void setTipoCuota(String tipoCuota) { this.tipoCuota = tipoCuota; }
    public LocalDate getFechaCaducidad() { return fechaCaducidad; }
    public void setFechaCaducidad(LocalDate fechaCaducidad) { this.fechaCaducidad = fechaCaducidad; }
}