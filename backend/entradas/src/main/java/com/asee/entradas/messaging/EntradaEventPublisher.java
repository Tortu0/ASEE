package com.asee.entradas.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Publica el evento asíncrono "entrada comprada" en RabbitMQ (apartado 3.4 de la memoria).
 *
 * Usuarios lo consume para añadir la actividad al historial del comprador. Cada mensaje
 * contiene: eventoId, usuarioId, referenciaId (la entrada), fecha, importe y descripcion.
 * El nombre del evento (que identifica su tipo) viaja como routing key y como propiedad
 * "type" del mensaje. No se incluyen datos de pago ni el código QR.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntradaEventPublisher {

    public static final String NOMBRE_EVENTO = "entrada.comprada";

    private final RabbitTemplate rabbitTemplate;

    @Value("${entradas.rabbit.exchange}")
    private String exchange;

    @Value("${entradas.rabbit.routing-key}")
    private String routingKey;

    /** Publica un evento por cada entrada comprada. */
    public void publicarEntradaComprada(EntradaCompradaEvent evento) {
        log.info("Publicando evento {} para la entrada {}", NOMBRE_EVENTO, evento.referenciaId());
        rabbitTemplate.convertAndSend(exchange, routingKey, evento, message -> {
            MessageProperties props = message.getMessageProperties();
            props.getHeaders().remove("__TypeId__");   // el consumidor usa su propia clase, no la nuestra
            props.setType(NOMBRE_EVENTO);
            props.setMessageId(evento.eventoId());
            return message;
        });
    }

    /**
     * Cuerpo del mensaje. La fecha va como texto ISO-8601 (date-time) y el importe en euros.
     * El eventoId es estable para una misma entrada: si el mensaje se reenvía, Usuarios
     * detecta el duplicado por este identificador.
     */
    public record EntradaCompradaEvent(
            String eventoId,
            String usuarioId,
            String referenciaId,
            String fecha,
            double importe,
            String descripcion
    ) {
        public static EntradaCompradaEvent desde(String entradaId, String usuarioId, Instant fecha,
                                                 double importe, String zona, int fila, int numero) {
            String eventoId = UUID.nameUUIDFromBytes(
                    (NOMBRE_EVENTO + ":" + entradaId).getBytes(StandardCharsets.UTF_8)).toString();
            String descripcion = "Entrada zona " + zona + ", fila " + fila + ", asiento " + numero;
            return new EntradaCompradaEvent(eventoId, usuarioId, entradaId, fecha.toString(), importe, descripcion);
        }
    }
}