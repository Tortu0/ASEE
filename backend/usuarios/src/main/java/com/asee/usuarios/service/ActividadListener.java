package com.asee.usuarios.service;

import com.asee.usuarios.config.RabbitMQConfig;
import com.asee.usuarios.dto.ActividadEventDTO;
import com.asee.usuarios.entity.Actividad;
import com.asee.usuarios.repository.ActividadRepository;
import com.asee.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Consume los eventos "entrada.comprada" y "pedido.creado" y los guarda en el historial del usuario
 * (memoria 3.4). El tipo de actividad ("entrada" | "pedido") se deduce de la routing key.
 */
@Service
public class ActividadListener {

    private static final Logger log = LoggerFactory.getLogger(ActividadListener.class);

    private final ActividadRepository actividadRepository;
    private final UsuarioRepository usuarioRepository;

    public ActividadListener(ActividadRepository actividadRepository, UsuarioRepository usuarioRepository) {
        this.actividadRepository = actividadRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ACTIVIDADES)
    public void recibirActividad(ActividadEventDTO evento,
                                 @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {

        String tipo = tipoDesdeRoutingKey(routingKey);
        if (tipo == null) {
            // Mensaje que no sabemos interpretar: se descarta (sin reencolar para no entrar en bucle)
            throw new AmqpRejectAndDontRequeueException("Routing key desconocida: " + routingKey);
        }
        if (evento.getUsuarioId() == null || evento.getReferenciaId() == null || evento.getReferenciaId().isBlank()) {
            throw new AmqpRejectAndDontRequeueException("Evento sin usuarioId o referenciaId (eventoId=" + evento.getEventoId() + ")");
        }
        if (!usuarioRepository.existsById(evento.getUsuarioId())) {
            log.warn("[RABBITMQ] Evento {} descartado: el usuario {} no existe", evento.getEventoId(), evento.getUsuarioId());
            return;
        }
        // IDEMPOTENCIA: un mismo evento reenviado no duplica la actividad
        if (actividadRepository.existsByUsuarioIdAndTipoAndReferenciaId(evento.getUsuarioId(), tipo, evento.getReferenciaId())) {
            log.info("[RABBITMQ] Evento duplicado ignorado: {} {}", tipo, evento.getReferenciaId());
            return;
        }

        Actividad actividad = new Actividad();
        actividad.setUsuarioId(evento.getUsuarioId());
        actividad.setTipo(tipo);
        actividad.setReferenciaId(evento.getReferenciaId());
        actividad.setImporte(evento.getImporte());
        actividad.setDescripcion(evento.getDescripcion());
        LocalDateTime fecha = ActividadService.parseFecha(evento.getFecha());
        if (fecha != null) {
            actividad.setFecha(fecha);
        }

        try {
            actividadRepository.save(actividad);
            log.info("[RABBITMQ] Actividad guardada: {} {} (usuario {})", tipo, evento.getReferenciaId(), evento.getUsuarioId());
        } catch (DataIntegrityViolationException e) {
            // Carrera entre dos entregas del mismo evento: la restricción única de la tabla lo detiene
            log.info("[RABBITMQ] Evento duplicado ignorado (restricción única): {} {}", tipo, evento.getReferenciaId());
        }
    }

    private static String tipoDesdeRoutingKey(String routingKey) {
        if (routingKey == null) return null;
        if (routingKey.startsWith("entrada")) return "entrada";
        if (routingKey.startsWith("pedido")) return "pedido";
        return null;
    }
}
