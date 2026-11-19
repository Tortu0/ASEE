package com.asee.estadisticas.service;

import com.asee.estadisticas.config.DeportivoProperties;
import com.asee.estadisticas.exception.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

/** Prueba el cliente de Deportivo contra un servidor HTTP local. No necesita MongoDB. */
class DeportivoClientTest {
    static final UUID P = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final UUID J = UUID.fromString("10000000-0000-0000-0000-000000000001");
    final AtomicInteger status = new AtomicInteger(200);
    final AtomicReference<String> body = new AtomicReference<>("{}");
    final AtomicReference<String> authorization = new AtomicReference<>();
    HttpServer server;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    private DeportivoClient client(String token) {
        var config = new DeportivoProperties(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"),
                Duration.ofSeconds(2), Duration.ofSeconds(2));
        return new DeportivoClient(config, JsonMapper.builder().build(), token);
    }
    private static String partidoJson(UUID id, String temporada) {
        return "{\"partidoId\":\"" + id + "\",\"temporada\":\"" + temporada + "\",\"competicion\":\"Liga\",\"jornada\":3,\"fechaHora\":\"2026-11-04T18:30:00Z\"}";
    }
    private static void esperaEstado(int esperado, Runnable accion) {
        assertThatThrownBy(accion::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(esperado));
    }

    @Test void partidoValidoSeConvierteEnReferencia() {
        body.set(partidoJson(P, "2026-2027"));
        var partido = client("").partido(P);
        assertThat(partido.partidoId()).isEqualTo(P);
        assertThat(partido.temporada()).isEqualTo("2026-2027");
        assertThat(partido.competicion()).isEqualTo("Liga");
        assertThat(partido.jornada()).isEqualTo(3);
        assertThat(partido.fechaHora()).isEqualTo(Instant.parse("2026-11-04T18:30:00Z"));
    }
    @Test void camposOpcionalesAusentesQuedanNulos() {
        body.set("{\"partidoId\":\"" + P + "\",\"temporada\":\"2026-2027\"}");
        var partido = client("").partido(P);
        assertThat(partido.competicion()).isNull();
        assertThat(partido.jornada()).isNull();
        assertThat(partido.fechaHora()).isNull();
    }
    @Test void partidoInexistenteEs404() {
        status.set(404);
        esperaEstado(404, () -> client("").partido(P));
    }
    @Test void errorDeDeportivoEs503() {
        status.set(500);
        esperaEstado(503, () -> client("").partido(P));
    }
    @Test void respuestaConOtroIdEs503() {
        body.set(partidoJson(UUID.randomUUID(), "2026-2027"));
        esperaEstado(503, () -> client("").partido(P));
    }
    @Test void temporadaConFormatoInvalidoEs503() {
        body.set(partidoJson(P, "2026"));
        esperaEstado(503, () -> client("").partido(P));
    }
    @Test void respuestaQueNoEsJsonEs503() {
        body.set("esto no es json");
        esperaEstado(503, () -> client("").partido(P));
    }
    @Test void deportivoCaidoEs503() {
        var cliente = client("");
        server.stop(0);
        esperaEstado(503, () -> cliente.partido(P));
        esperaEstado(503, () -> cliente.jugador(J));
    }
    @Test void jugadorValidoNoLanzaError() {
        body.set("{\"jugadorId\":\"" + J + "\"}");
        assertThatCode(() -> client("").jugador(J)).doesNotThrowAnyException();
    }
    @Test void jugadorInexistenteEs404() {
        status.set(404);
        esperaEstado(404, () -> client("").jugador(J));
    }
    @Test void jugadorConOtroIdEs503() {
        body.set("{\"jugadorId\":\"" + UUID.randomUUID() + "\"}");
        esperaEstado(503, () -> client("").jugador(J));
    }
    @Test void tokenDeServicioSoloSeEnviaSiEstaConfigurado() {
        body.set("{\"jugadorId\":\"" + J + "\"}");
        client("").jugador(J);
        assertThat(authorization.get()).isNull();
        client("abc").jugador(J);
        assertThat(authorization.get()).isEqualTo("Bearer abc");
    }
}
