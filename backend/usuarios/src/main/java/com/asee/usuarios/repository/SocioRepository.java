package com.asee.usuarios.repository;

import com.asee.usuarios.entity.Socio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface SocioRepository extends JpaRepository<Socio, UUID> {

    // @Modifying indica que no es una consulta SELECT, sino que va a modificar datos.
    // Esta consulta busca a todos los socios activos cuya fecha de caducidad sea anterior a HOY,
    // y los pasa a "caducado" con descuento 0 en una sola operación ultrarrápida.
    @Modifying
    @Query("UPDATE Socio s SET s.estado = 'caducado', s.descuentoPorcentaje = 0 WHERE s.estado = 'activo' AND s.fechaCaducidad < CURRENT_DATE")
    int expirarCuotasCaducadas();
}