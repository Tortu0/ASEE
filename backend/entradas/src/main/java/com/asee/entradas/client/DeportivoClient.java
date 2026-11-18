package com.asee.entradas.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Cliente HTTP para consultar el microservicio Deportivo.
 *
 * Comunicación síncrona: Entradas → Deportivo → GET /partidos/{partidoId}
 */
@Slf4j
@Component
public class DeportivoClient {

    private final WebClient webClient;

    public DeportivoClient(@Qualifier("deportivoWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Verifica que el partido existe y tiene la venta de entradas abierta.
     *
     * @return true si existe y venta abierta; false si no existe; lanza
     *         {@link DeportivoNoDisponibleException} si el servicio está caído.
     */
    public PartidoInfo obtenerPartido(String partidoId) {
        try {
            return webClient.get()
                    .uri("/partidos/{id}", partidoId)
                    .retrieve()
                    .onStatus(HttpStatus.NOT_FOUND::equals, resp ->
                            Mono.error(new PartidoNoEncontradoException(partidoId)))
                    .bodyToMono(PartidoInfo.class)
                    .block(Duration.ofSeconds(3));
        } catch (PartidoNoEncontradoException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.error("Error HTTP del microservicio Deportivo: {}", e.getStatusCode());
            throw new DeportivoNoDisponibleException(e);
        } catch (Exception e) {
            log.error("Microservicio Deportivo no disponible", e);
            throw new DeportivoNoDisponibleException(e);
        }
    }

    /** Subconjunto de datos del partido que necesita Entradas. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    /**
     * Deportivo expone este dato como "ventaEntradasAbierta"; en Entradas lo llamamos ventaAbierta.
     */
    public record PartidoInfo(String partidoId,
                              @JsonProperty("ventaEntradasAbierta") boolean ventaAbierta) {}

    public static class PartidoNoEncontradoException extends RuntimeException {
        public PartidoNoEncontradoException(String id) {
            super("Partido no encontrado en Deportivo: " + id);
        }
    }

    public static class DeportivoNoDisponibleException extends RuntimeException {
        public DeportivoNoDisponibleException(Throwable cause) {
            super("El microservicio Deportivo no está disponible", cause);
        }
    }
}