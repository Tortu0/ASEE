package com.asee.entradas.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EntradaEventPublisherTest {

    @Mock RabbitTemplate rabbitTemplate;

    private EntradaEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new EntradaEventPublisher(rabbitTemplate);
        ReflectionTestUtils.setField(publisher, "exchange", "club.exchange");
        ReflectionTestUtils.setField(publisher, "routingKey", "entrada.comprada");
    }

    private static EntradaEventPublisher.EntradaCompradaEvent evento() {
        return EntradaEventPublisher.EntradaCompradaEvent.desde(
                "entrada-1", "usuario-1", Instant.parse("2026-05-01T18:30:00Z"), 7.65, "A", 3, 7);
    }

    @Test
    void publicaEnElExchangeYLaRoutingKeyConfigurados() {
        EntradaEventPublisher.EntradaCompradaEvent ev = evento();

        publisher.publicarEntradaComprada(ev);

        verify(rabbitTemplate).convertAndSend(
                eq("club.exchange"), eq("entrada.comprada"), eq(ev), org.mockito.ArgumentMatchers.any(MessagePostProcessor.class));
    }

    @Test
    void elPostProcesadorFijaTipoIdDeMensajeYQuitaElTypeIdDeJava() throws Exception {
        EntradaEventPublisher.EntradaCompradaEvent ev = evento();
        publisher.publicarEntradaComprada(ev);

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq("club.exchange"), eq("entrada.comprada"), eq(ev), captor.capture());

        MessageProperties props = new MessageProperties();
        props.getHeaders().put("__TypeId__", "com.asee.entradas.messaging.EntradaEventPublisher$EntradaCompradaEvent");
        Message procesado = captor.getValue().postProcessMessage(new Message(new byte[0], props));

        assertThat(procesado.getMessageProperties().getType()).isEqualTo("entrada.comprada");
        assertThat(procesado.getMessageProperties().getMessageId()).isEqualTo(ev.eventoId());
        assertThat(procesado.getMessageProperties().getHeaders()).doesNotContainKey("__TypeId__");
    }

    @Test
    void elEventoContieneLosDatosDeLaEntradaSinDatosSensibles() {
        EntradaEventPublisher.EntradaCompradaEvent ev = evento();

        assertThat(ev.usuarioId()).isEqualTo("usuario-1");
        assertThat(ev.referenciaId()).isEqualTo("entrada-1");
        assertThat(ev.fecha()).isEqualTo("2026-05-01T18:30:00Z");
        assertThat(ev.importe()).isEqualTo(7.65);
        assertThat(ev.descripcion()).isEqualTo("Entrada zona A, fila 3, asiento 7");
        // solo estos campos: no hay datos de pago ni código QR
        assertThat(EntradaEventPublisher.EntradaCompradaEvent.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("eventoId", "usuarioId", "referenciaId", "fecha", "importe", "descripcion");
    }

    @Test
    void elEventoIdEsEstableParaLaMismaEntradaYDistintoParaOtra() {
        String esperado = UUID.nameUUIDFromBytes("entrada.comprada:entrada-1".getBytes(StandardCharsets.UTF_8)).toString();

        assertThat(evento().eventoId()).isEqualTo(esperado);
        assertThat(evento().eventoId()).isEqualTo(evento().eventoId());
        assertThat(EntradaEventPublisher.EntradaCompradaEvent.desde(
                "entrada-2", "usuario-1", Instant.now(), 1, "A", 1, 1).eventoId()).isNotEqualTo(esperado);
    }

    @Test
    void elNombreDelEventoEsEntradaComprada() {
        assertThat(EntradaEventPublisher.NOMBRE_EVENTO).isEqualTo("entrada.comprada");
    }
}
