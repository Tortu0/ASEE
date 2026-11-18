package com.asee.usuarios.repository;

import com.asee.usuarios.entity.Actividad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ActividadRepository extends JpaRepository<Actividad, UUID> {
    // Nos servirá luego para el endpoint GET de consultar historial
    List<Actividad> findByUsuarioIdOrderByFechaDesc(UUID usuarioId);

    // Para ignorar eventos repetidos sin depender de capturar la excepción de la base de datos
    boolean existsByUsuarioIdAndTipoAndReferenciaId(UUID usuarioId, String tipo, String referenciaId);
}