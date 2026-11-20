package com.asee.entradas.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas de {@link UsuariosClient} con un ExchangeFunction simulado (sin red ni servidor). */
class UsuariosClientTest {

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }

    private static UsuariosClient cliente(ExchangeFunction fn) {
        return new UsuariosClient(WebClient.builder().baseUrl("http://usuarios/v1").exchangeFunction(fn).build());
    }

    private static void autenticarComo(String userId, String rol) {
        Jwt jwt = Jwt.withTokenValue("token-123").header("alg", "HS256")
                .subject(userId).claim("rol", rol).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void obtenerInfoSocio_socioActivo() {
        AtomicReference<ClientRequest> enviada = new AtomicReference<>();
        UsuariosClient client = cliente(req -> {
            enviada.set(req);
            return Mono.just(json(HttpStatus.OK,
                    "{\"usuarioId\":\"u1\",\"estado\":\"activo\",\"descuentoPorcentaje\":15.0,\"extra\":1}"));
        });

        UsuariosClient.SocioInfo info = client.obtenerInfoSocio("u1");

        assertThat(info.usuarioId()).isEqualTo("u1");
        assertThat(info.socioActivo()).isTrue();
        assertThat(info.descuento()).isEqualTo(15.0);
        assertThat(enviada.get().method()).isEqualTo(HttpMethod.GET);
        assertThat(enviada.get().url().toString()).isEqualTo("http://usuarios/v1/usuarios/u1/socio");
    }

    @Test
    void obtenerInfoSocio_reenviaElJwtYLaIdentidadDelComprador() {
        autenticarComo("comprador-1", "socio");
        AtomicReference<ClientRequest> enviada = new AtomicReference<>();
        UsuariosClient client = cliente(req -> {
            enviada.set(req);
            return Mono.just(json(HttpStatus.OK, "{\"usuarioId\":\"u1\",\"estado\":\"no_socio\",\"descuentoPorcentaje\":0}"));
        });

        client.obtenerInfoSocio("u1");

        HttpHeaders h = enviada.get().headers();
        assertThat(h.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token-123");
        assertThat(h.getFirst("X-User-Id")).isEqualTo("comprador-1");
        assertThat(h.getFirst("X-User-Role")).isEqualTo("socio");
    }

    @Test
    void obtenerInfoSocio_sinAutenticacion_noEnviaCabecerasDeIdentidad() {
        AtomicReference<ClientRequest> enviada = new AtomicReference<>();
        UsuariosClient client = cliente(req -> {
            enviada.set(req);
            return Mono.just(json(HttpStatus.OK, "{\"usuarioId\":\"u1\",\"estado\":\"activo\",\"descuentoPorcentaje\":5}"));
        });

        client.obtenerInfoSocio("u1");

        HttpHeaders h = enviada.get().headers();
        assertThat(h.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(h.containsKey("X-User-Id")).isFalse();
        assertThat(h.containsKey("X-User-Role")).isFalse();
    }

    @Test
    void obtenerInfoSocio_404_lanzaUsuarioNoEncontrado() {
        UsuariosClient client = cliente(req -> Mono.just(json(HttpStatus.NOT_FOUND, "{}")));

        assertThatThrownBy(() -> client.obtenerInfoSocio("u1"))
                .isInstanceOf(UsuariosClient.UsuarioNoEncontradoException.class)
                .hasMessageContaining("u1");
    }

    @Test
    void obtenerInfoSocio_503_lanzaUsuariosNoDisponible() {
        UsuariosClient client = cliente(req -> Mono.just(json(HttpStatus.SERVICE_UNAVAILABLE, "{}")));

        assertThatThrownBy(() -> client.obtenerInfoSocio("u1"))
                .isInstanceOf(UsuariosClient.UsuariosNoDisponibleException.class)
                .hasCauseInstanceOf(WebClientResponseException.class);
    }

    @Test
    void obtenerInfoSocio_errorDeConexion_lanzaUsuariosNoDisponible() {
        UsuariosClient client = cliente(req -> Mono.error(new IllegalStateException("timeout")));

        assertThatThrownBy(() -> client.obtenerInfoSocio("u1"))
                .isInstanceOf(UsuariosClient.UsuariosNoDisponibleException.class);
    }

    @Test
    void socioActivo_soloParaEstadoActivoSinImportarMayusculas() {
        assertThat(new UsuariosClient.SocioInfo("u", "activo", 0).socioActivo()).isTrue();
        assertThat(new UsuariosClient.SocioInfo("u", "ACTIVO", 0).socioActivo()).isTrue();
        assertThat(new UsuariosClient.SocioInfo("u", "caducado", 10).socioActivo()).isFalse();
        assertThat(new UsuariosClient.SocioInfo("u", "no_socio", 0).socioActivo()).isFalse();
        assertThat(new UsuariosClient.SocioInfo("u", null, 0).socioActivo()).isFalse();
    }
}
