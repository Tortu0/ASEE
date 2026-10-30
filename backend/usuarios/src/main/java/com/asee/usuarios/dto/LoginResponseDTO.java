package com.asee.usuarios.dto;

public class LoginResponseDTO {
    private String token;
    private String tipo = "Bearer";
    private int expiraEn;
    private UsuarioResponseDTO usuario; // Reutilizamos el DTO que ya creaste sin la contraseña

    public LoginResponseDTO() {}

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public int getExpiraEn() { return expiraEn; }
    public void setExpiraEn(int expiraEn) { this.expiraEn = expiraEn; }
    public UsuarioResponseDTO getUsuario() { return usuario; }
    public void setUsuario(UsuarioResponseDTO usuario) { this.usuario = usuario; }
}