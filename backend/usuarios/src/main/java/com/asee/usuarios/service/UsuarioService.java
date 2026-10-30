package com.asee.usuarios.service;

import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    // Inyección de dependencias mediante constructor (Buenas prácticas en Spring)
    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario registrarUsuario(String nombre, String email, String passwordPlana, String telefono, String direccion) {
        // 1. Regla de negocio: Comprobar si el email ya existe para evitar duplicados
        if (usuarioRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        // 2. Crear la entidad
        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setNombre(nombre);
        nuevoUsuario.setEmail(email);
        nuevoUsuario.setTelefono(telefono);
        nuevoUsuario.setDireccion(direccion);

        // 3. Regla de negocio: Encriptar la contraseña (Hash + Salt automático con Argon2id)
        nuevoUsuario.setPassword(passwordEncoder.encode(passwordPlana));

        // 4. Regla de negocio: Rol "visitante" por defecto según especificación
        nuevoUsuario.setRol("visitante");

        // 5. Persistir en la base de datos PostgreSQL
        return usuarioRepository.save(nuevoUsuario);
    }

    public Optional<Usuario> obtenerUsuario(UUID id) {
        return usuarioRepository.findById(id);
    }

    // Método interno que usaremos más adelante para el Login
    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }
}