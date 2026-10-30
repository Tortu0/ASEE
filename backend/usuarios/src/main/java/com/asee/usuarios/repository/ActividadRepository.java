package com.asee.usuarios.repository;

import com.asee.usuarios.entity.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ActividadRepository extends JpaRepository<Actividad, Long> {

    // Traduce a: SELECT * FROM actividades WHERE usuario_id = ? ORDER BY fecha DESC
    // Necesario para el endpoint GET /usuarios/{usuarioId}/historial
    List<Actividad> findByUsuarioIdOrderByFechaDesc(UUID usuarioId);

    // El YAML y la memoria (apartado 3.4) indican que la combinación de usuario, tipo y referencia debe ser única,
    // y si llega un evento duplicado desde RabbitMQ, no debe registrarse otra vez.
    boolean existsByUsuarioIdAndTipoAndReferenciaId(UUID usuarioId, String tipo, UUID referenciaId);
}
