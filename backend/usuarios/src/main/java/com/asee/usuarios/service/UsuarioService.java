package com.asee.usuarios.service;

import com.asee.usuarios.entity.Usuario;
import com.asee.usuarios.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.dto.UsuarioCreateDTO;

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

    public Usuario registrarUsuario(UsuarioCreateDTO dto) {
        // 1. Regla de negocio: Comprobar si el email ya existe para evitar duplicados
        if (usuarioRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        // 2. Crear la entidad extrayendo los datos del DTO
        Usuario nuevoUsuario = new Usuario();
        nuevoUsuario.setNombre(dto.getNombre());
        nuevoUsuario.setEmail(dto.getEmail());
        nuevoUsuario.setTelefono(dto.getTelefono());
        nuevoUsuario.setDireccion(dto.getDireccion());

        // 3. Regla de negocio: Encriptar la contraseña
        nuevoUsuario.setPassword(passwordEncoder.encode(dto.getPassword()));

        // 4. Regla de negocio: Rol "visitante" por defecto
        nuevoUsuario.setRol(Rol.visitante);

        // 5. Persistir en la base de datos
        return usuarioRepository.save(nuevoUsuario);
    }

    public Optional<Usuario> obtenerUsuario(UUID id) {
        return usuarioRepository.findById(id);
    }

    // Método interno que usaremos más adelante para el Login
    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    public Usuario actualizarUsuario(UUID id, com.asee.usuarios.dto.UsuarioUpdateDTO dto) {
        // Buscamos al usuario o lanzamos excepción si no existe
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Actualización parcial campo por campo
        if (dto.getNombre() != null && !dto.getNombre().isBlank()) {
            usuario.setNombre(dto.getNombre());
        }
        if (dto.getTelefono() != null) {
            usuario.setTelefono(dto.getTelefono());
        }
        if (dto.getDireccion() != null) {
            usuario.setDireccion(dto.getDireccion());
        }

        return usuarioRepository.save(usuario);
    }

    // Método para DELETE
    public void eliminarUsuario(UUID id) {
        if (!usuarioRepository.existsById(id)) {
            throw new IllegalArgumentException("Usuario no encontrado");
        }
        usuarioRepository.deleteById(id);
    }

    // Método para PUT rol
    public Usuario cambiarRol(UUID id, com.asee.usuarios.entity.Rol nuevoRol) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        usuario.setRol(nuevoRol);
        return usuarioRepository.save(usuario);
    }
}