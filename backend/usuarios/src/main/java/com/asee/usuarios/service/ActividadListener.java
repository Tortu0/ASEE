package com.asee.usuarios.service;

import com.asee.usuarios.config.RabbitMQConfig;
import com.asee.usuarios.dto.ActividadEventDTO;
import com.asee.usuarios.entity.Actividad;
import com.asee.usuarios.repository.ActividadRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class ActividadListener {

    private final ActividadRepository actividadRepository;

    public ActividadListener(ActividadRepository actividadRepository) {
        this.actividadRepository = actividadRepository;
    }

    // Spring Boot ejecutará este método automáticamente cada vez que llegue un mensaje a la cola
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ACTIVIDADES)
    public void recibirActividad(ActividadEventDTO evento) {
        System.out.println("[RABBITMQ] Mensaje recibido para usuario: " + evento.getUsuarioId());

        Actividad nuevaActividad = new Actividad();
        nuevaActividad.setUsuarioId(evento.getUsuarioId());
        nuevaActividad.setTipo(evento.getTipo());
        nuevaActividad.setReferenciaId(evento.getReferenciaId());

        try {
            // Intentamos guardar. Si la base de datos lo acepta, perfecto.
            actividadRepository.save(nuevaActividad);
            System.out.println("[RABBITMQ] Actividad guardada correctamente.");

        } catch (DataIntegrityViolationException e) {
            // IDEMPOTENCIA ACTIVA (Nuestra Opción B)
            // Si PostgreSQL detecta que la combinación (usuarioId, tipo, referenciaId) ya existe,
            // lanza esta excepción. Nosotros la capturamos silenciosamente e ignoramos el mensaje repetido.
            System.out.println("[RABBITMQ] EVENTO DUPLICADO IGNORADO. Ya existía la actividad: " + evento.getReferenciaId());
        }
    }
}