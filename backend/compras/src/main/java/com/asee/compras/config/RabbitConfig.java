package com.asee.compras.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "compras.exchange";
    public static final String QUEUE_PEDIDO_CREADO = "compras.pedido.creado";
    public static final String ROUTING_KEY_PEDIDO_CREADO = "pedido.creado";

    @Bean
    public TopicExchange comprasExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue pedidoCreadoQueue() {
        return new Queue(QUEUE_PEDIDO_CREADO, true);
    }

    @Bean
    public Binding pedidoCreadoBinding(Queue pedidoCreadoQueue, TopicExchange comprasExchange) {
        return BindingBuilder.bind(pedidoCreadoQueue)
                .to(comprasExchange)
                .with(ROUTING_KEY_PEDIDO_CREADO);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}