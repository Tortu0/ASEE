package com.asee.compras.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ: Compras publica en el exchange compartido "club.exchange" (el mismo que declaran Usuarios y Entradas).
 * También declara la cola de actividades de Usuarios y su enlace con "pedido.creado" (con los mismos atributos que
 * allí), de modo que los eventos no se pierdan si Usuarios todavía no está en marcha cuando se publican.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "club.exchange";
    public static final String QUEUE_ACTIVIDADES_USUARIOS = "q.usuarios.actividades";
    public static final String ROUTING_KEY_PEDIDO_CREADO = "pedido.creado";

    @Bean
    public TopicExchange clubExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue actividadesUsuariosQueue() {
        return new Queue(QUEUE_ACTIVIDADES_USUARIOS, true);
    }

    @Bean
    public Binding pedidoCreadoBinding(Queue actividadesUsuariosQueue, TopicExchange clubExchange) {
        return BindingBuilder.bind(actividadesUsuariosQueue).to(clubExchange).with(ROUTING_KEY_PEDIDO_CREADO);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
