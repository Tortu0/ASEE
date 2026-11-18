package com.asee.usuarios.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "socios")
public class Socio {

    @Id
    private UUID usuarioId;

    // Magia JPA: Relaciona este Socio con un Usuario existente.
    // @MapsId le dice que use el ID del Usuario como su propio ID.
    @OneToOne
    @MapsId
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "numero_socio", unique = true, length = 50)
    private String numeroSocio;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(name = "tipo_cuota", length = 20)
    private String tipoCuota;

    @Column(name = "fecha_alta")
    private LocalDate fechaAlta;

    @Column(name = "fecha_caducidad")
    private LocalDate fechaCaducidad;

    @Column(name = "descuento_porcentaje", precision = 5, scale = 2)
    private BigDecimal descuentoPorcentaje;

    public Socio() {
    }

    public Socio(Usuario usuario, String estado, BigDecimal descuentoPorcentaje) {
        this.usuario = usuario;
        this.estado = estado;
        this.descuentoPorcentaje = descuentoPorcentaje;
    }

    // --- GETTERS Y SETTERS ---

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(UUID usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public String getNumeroSocio() {
        return numeroSocio;
    }

    public void setNumeroSocio(String numeroSocio) {
        this.numeroSocio = numeroSocio;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getTipoCuota() {
        return tipoCuota;
    }

    public void setTipoCuota(String tipoCuota) {
        this.tipoCuota = tipoCuota;
    }

    public LocalDate getFechaAlta() {
        return fechaAlta;
    }

    public void setFechaAlta(LocalDate fechaAlta) {
        this.fechaAlta = fechaAlta;
    }

    public LocalDate getFechaCaducidad() {
        return fechaCaducidad;
    }

    public void setFechaCaducidad(LocalDate fechaCaducidad) {
        this.fechaCaducidad = fechaCaducidad;
    }

    public BigDecimal getDescuentoPorcentaje() {
        return descuentoPorcentaje;
    }

    public void setDescuentoPorcentaje(BigDecimal descuentoPorcentaje) {
        this.descuentoPorcentaje = descuentoPorcentaje;
    }
}
