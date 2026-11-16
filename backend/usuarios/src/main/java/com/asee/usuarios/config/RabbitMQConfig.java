package com.asee.usuarios.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 1. Nuestro buzón exclusivo (durable: sobrevive a un reinicio de RabbitMQ)
    public static final String QUEUE_ACTIVIDADES = "q.usuarios.actividades";

    // 2. Exchange compartido del club (el mismo que declara Entradas) y los eventos que nos interesan.
    //    El TIPO de actividad se deduce de la routing key, no del cuerpo del mensaje.
    public static final String EXCHANGE = "club.exchange";
    public static final String RK_ENTRADA_COMPRADA = "entrada.comprada";
    public static final String RK_PEDIDO_CREADO = "pedido.creado";

    @Bean
    public Queue actividadesQueue() {
        return new Queue(QUEUE_ACTIVIDADES, true);
    }

    // Mismos atributos que en Entradas (topic, durable), para que RabbitMQ no rechace la redeclaración
    @Bean
    public TopicExchange clubExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    // Sin estos enlaces la cola existía pero nada llegaba a ella
    @Bean
    public Binding bindingEntradaComprada(Queue actividadesQueue, TopicExchange clubExchange) {
        return BindingBuilder.bind(actividadesQueue).to(clubExchange).with(RK_ENTRADA_COMPRADA);
    }

    @Bean
    public Binding bindingPedidoCreado(Queue actividadesQueue, TopicExchange clubExchange) {
        return BindingBuilder.bind(actividadesQueue).to(clubExchange).with(RK_PEDIDO_CREADO);
    }

    // Enseñamos a RabbitMQ a traducir de JSON a objetos Java automáticamente
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
