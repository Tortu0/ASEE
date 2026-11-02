package com.asee.usuarios.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 1. Declaramos el nombre de nuestra cola (nuestro buzón exclusivo)
    public static final String QUEUE_ACTIVIDADES = "q.usuarios.actividades";

    @Bean
    public Queue actividadesQueue() {
        // true significa que la cola sobrevivirá si reiniciamos RabbitMQ (duradera)
        return new Queue(QUEUE_ACTIVIDADES, true);
    }

    // 2. Enseñamos a RabbitMQ a traducir de JSON a objetos Java automáticamente
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}