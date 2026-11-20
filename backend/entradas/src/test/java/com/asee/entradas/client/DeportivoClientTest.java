package com.asee.entradas.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas de {@link DeportivoClient} con un ExchangeFunction simulado (sin red ni servidor). */
class DeportivoClientTest {

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static DeportivoClient cliente(ExchangeFunction fn) {
        return new DeportivoClient(WebClient.builder().baseUrl("http://deportivo/v1").exchangeFunction(fn).build());
    }

    @Test
    void obtenerPartido_ventaAbierta() {
        AtomicReference<ClientRequest> enviada = new AtomicReference<>();
        DeportivoClient client = cliente(req -> {
            enviada.set(req);
            return Mono.just(json(HttpStatus.OK,
                    "{\"partidoId\":\"p1\",\"ventaEntradasAbierta\":true,\"rival\":\"X\"}"));
        });

        DeportivoClient.PartidoInfo info = client.obtenerPartido("p1");

        assertThat(info.partidoId()).isEqualTo("p1");
        assertThat(info.ventaAbierta()).isTrue();
        assertThat(enviada.get().method()).isEqualTo(HttpMethod.GET);
        assertThat(enviada.get().url().toString()).isEqualTo("http://deportivo/v1/partidos/p1");
    }

    @Test
    void obtenerPartido_ventaCerrada() {
        DeportivoClient client = cliente(req -> Mono.just(json(HttpStatus.OK,
                "{\"partidoId\":\"p1\",\"ventaEntradasAbierta\":false}")));

        assertThat(client.obtenerPartido("p1").ventaAbierta()).isFalse();
    }

    @Test
    void obtenerPartido_404_lanzaPartidoNoEncontrado() {
        DeportivoClient client = cliente(req -> Mono.just(json(HttpStatus.NOT_FOUND, "{}")));

        assertThatThrownBy(() -> client.obtenerPartido("p1"))
                .isInstanceOf(DeportivoClient.PartidoNoEncontradoException.class)
                .hasMessageContaining("p1");
    }

    @Test
    void obtenerPartido_500_lanzaDeportivoNoDisponible() {
        DeportivoClient client = cliente(req -> Mono.just(json(HttpStatus.INTERNAL_SERVER_ERROR, "{}")));

        assertThatThrownBy(() -> client.obtenerPartido("p1"))
                .isInstanceOf(DeportivoClient.DeportivoNoDisponibleException.class)
                .hasCauseInstanceOf(WebClientResponseException.class);
    }

    @Test
    void obtenerPartido_errorDeConexion_lanzaDeportivoNoDisponible() {
        DeportivoClient client = cliente(req -> Mono.error(new IllegalStateException("conexión rechazada")));

        assertThatThrownBy(() -> client.obtenerPartido("p1"))
                .isInstanceOf(DeportivoClient.DeportivoNoDisponibleException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }
}
