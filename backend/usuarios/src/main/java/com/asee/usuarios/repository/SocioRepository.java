package com.asee.usuarios.repository;

import com.asee.usuarios.entity.Socio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SocioRepository extends JpaRepository<Socio, UUID> {
    // Al ser una relación 1 a 1 por ID, nos basta con los métodos por defecto
    // heredados de JpaRepository (como findById). No necesitamos consultas custom de momento.
}