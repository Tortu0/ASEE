package com.asee.estadisticas.service;

import com.asee.estadisticas.config.DeportivoProperties;
import com.asee.estadisticas.exception.ApiException;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DeportivoClient {
    public record Partido(UUID partidoId, String temporada, String competicion, Integer jornada, Instant fechaHora) {}
    private final DeportivoProperties config;
    private final HttpClient http;
    private final JsonMapper json;
    private final String token;
    public DeportivoClient(DeportivoProperties config, JsonMapper json, @Value("${app.deportivo.service-token:}") String token) {
        this.config = config; this.json = json; this.token = token;
        http = HttpClient.newBuilder().connectTimeout(config.connectTimeout()).build();
    }
    public Partido partido(UUID id) {
        JsonNode value = get("/partidos/" + id);
        try {
            String temporada = value.path("temporada").asString();
            if (!temporada.matches("[0-9]{4}-[0-9]{4}") || !id.toString().equals(value.path("partidoId").asString())) throw new IllegalArgumentException();
            return new Partido(id, temporada, value.path("competicion").isTextual() ? value.path("competicion").asString() : null,
                    value.path("jornada").isIntegralNumber() ? value.path("jornada").asInt() : null,
                    value.path("fechaHora").isTextual() ? Instant.parse(value.path("fechaHora").asString()) : null);
        } catch (RuntimeException ex) { throw unavailable(); }
    }
    public void jugador(UUID id) {
        if (!id.toString().equals(get("/jugadores/" + id).path("jugadorId").asString())) throw unavailable();
    }
    private JsonNode get(String path) {
        try {
            String base = config.baseUrl().toString().replaceAll("/$", "");
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(config.readTimeout()).header("Accept", "application/json");
            if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
            var response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) throw ApiException.missing("Recurso no encontrado en Deportivo");
            if (response.statusCode() != 200) throw unavailable();
            return json.readTree(response.body());
        } catch (ApiException ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw unavailable(); }
        catch (Exception ex) { throw unavailable(); }
    }
    private ApiException unavailable() { return new ApiException(503, "No se puede validar el recurso en Deportivo"); }
}
