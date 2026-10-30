package com.asee.usuarios.repository;

import com.asee.usuarios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    // Spring traduce esto a: SELECT * FROM usuarios WHERE email = ?
    // Usamos Optional porque puede que el email no exista en la base de datos.
    Optional<Usuario> findByEmail(String email);

    // Spring traduce esto a: SELECT COUNT(*) > 0 FROM usuarios WHERE email = ?
    // Vital para devolver el error 409 del YAML al registrar si el email ya existe.
    boolean existsByEmail(String email);
}