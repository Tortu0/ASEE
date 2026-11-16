package com.asee.entradas.client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import com.asee.entradas.security.JwtUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Cliente HTTP para consultar el microservicio Usuarios.
 *
 * Comunicación síncrona: Entradas → Usuarios → GET /usuarios/{usuarioId}/socio
 */
@Slf4j
@Component
public class UsuariosClient {

    private final WebClient webClient;

    public UsuariosClient(@Qualifier("usuariosWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Consulta si un usuario es socio activo y obtiene el porcentaje de descuento.
     *
     * @return información de socio; lanza excepciones tipadas para 404 / 503.
     */
    public SocioInfo obtenerInfoSocio(String usuarioId) {
        // Usuarios NO valida el JWT: se fía de X-User-Id / X-User-Role (que normalmente inyecta el Gateway).
        // Como esta llamada va directa, se reenvían además la identidad y el rol verificados del comprador.
        String token = tokenActual();
        String[] identidad = identidadActual();
        try {
            return webClient.get()
                    .uri("/usuarios/{id}/socio", usuarioId)
                    .headers(h -> {
                        if (token != null) h.setBearerAuth(token);
                        if (identidad != null) {
                            h.set("X-User-Id", identidad[0]);
                            h.set("X-User-Role", identidad[1]);
                        }
                    })
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, resp ->
                            Mono.error(new UsuarioNoEncontradoException(usuarioId)))
                    .bodyToMono(SocioInfo.class)
                    .block(Duration.ofSeconds(3));
        } catch (UsuarioNoEncontradoException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.error("Error HTTP del microservicio Usuarios: {}", e.getStatusCode());
            throw new UsuariosNoDisponibleException(e);
        } catch (Exception e) {
            log.error("Microservicio Usuarios no disponible", e);
            throw new UsuariosNoDisponibleException(e);
        }
    }

    /** {id, rol} del usuario autenticado en la petición actual, o null si no hay JWT. */
    private static String[] identidadActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt) {
            JwtUser u = JwtUser.from(auth);
            return new String[] { u.getUserId(), u.getRole() };
        }
        return null;
    }

    /** JWT de la petición que se está atendiendo (la llamada se hace en el mismo hilo). */
    private static String tokenActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return jwt.getTokenValue();
        }
        return null;
    }

    /**
     * Respuesta de GET /usuarios/{id}/socio. Solo el estado "activo" da derecho a descuento
     * (los estados "caducado" y "no_socio" llegan con descuentoPorcentaje 0).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SocioInfo(String usuarioId,
                            String estado,
                            @JsonProperty("descuentoPorcentaje") double descuento) {
        @JsonIgnore
        public boolean socioActivo() {
            return "activo".equalsIgnoreCase(estado);
        }
    }

    public static class UsuarioNoEncontradoException extends RuntimeException {
        public UsuarioNoEncontradoException(String id) {
            super("Usuario no encontrado en Usuarios: " + id);
        }
    }

    public static class UsuariosNoDisponibleException extends RuntimeException {
        public UsuariosNoDisponibleException(Throwable cause) {
            super("El microservicio Usuarios no está disponible", cause);
        }
    }
}