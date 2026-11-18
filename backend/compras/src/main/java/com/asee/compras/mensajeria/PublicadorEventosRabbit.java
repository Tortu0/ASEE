package com.asee.compras.mensajeria;

import com.asee.compras.aplicacion.PublicadorEventos;
import com.asee.compras.config.RabbitConfig;
import com.asee.compras.dominio.EventoCompra;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica "pedido creado" en el exchange compartido del club (memoria 3.4). Usuarios lo consume para el historial.
 * El nombre del evento viaja como routing key y como propiedad "type"; el cuerpo no incluye datos de pago.
 */
@Component
public class PublicadorEventosRabbit implements PublicadorEventos {

    private final RabbitTemplate rabbit;

    public PublicadorEventosRabbit(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @Override
    public void publicar(String routingKey, EventoCompra evento) {
        rabbit.convertAndSend(RabbitConfig.EXCHANGE, routingKey, evento, message -> {
            MessageProperties props = message.getMessageProperties();
            props.getHeaders().remove("__TypeId__");   // el consumidor usa su propia clase, no la nuestra
            props.setType(routingKey);
            props.setMessageId(evento.eventoId());
            return message;
        });
    }
}
