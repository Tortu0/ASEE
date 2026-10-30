package com.asee.usuarios.dto;

public class UsuarioUpdateDTO {
    private String nombre;
    private String telefono;
    private String direccion;

    public UsuarioUpdateDTO() {}

    // Método auxiliar para cumplir la regla del YAML: "rechaza un cuerpo vacío con 400"
    public boolean isVacio() {
        return nombre == null && telefono == null && direccion == null;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
}