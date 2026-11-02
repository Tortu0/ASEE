package com.asee.entradas.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ: exchange topic, cola y binding para el evento
 * "entrada comprada".
 */
@Configuration
public class RabbitConfig {

    @Value("${entradas.rabbit.exchange}")
    private String exchange;

    @Value("${entradas.rabbit.routing-key}")
    private String routingKey;

    @Bean
    public TopicExchange clubExchange() {
        return ExchangeBuilder.topicExchange(exchange).durable(true).build();
    }

    @Bean
    public Queue entradaCompradaQueue() {
        return QueueBuilder.durable("entrada.comprada.queue").build();
    }

    @Bean
    public Binding entradaCompradaBinding() {
        return BindingBuilder
                .bind(entradaCompradaQueue())
                .to(clubExchange())
                .with(routingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
