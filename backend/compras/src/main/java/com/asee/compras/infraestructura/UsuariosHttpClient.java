package com.asee.compras.infraestructura;

import com.asee.compras.aplicacion.ApiException;
import com.asee.compras.aplicacion.UsuariosPort;
import com.asee.compras.dominio.Identidad;
import com.asee.compras.dominio.Socio;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Cliente de Compras -> Usuarios (GET /usuarios/{id}/socio). Usa el cliente HTTP del JDK.
 * Usuarios no valida el JWT: se fía de X-User-Id / X-User-Role, así que se reenvían la identidad y el rol
 * del comprador (y el Bearer original, por si Usuarios llegase a validarlo).
 */
public class UsuariosHttpClient implements UsuariosPort {

    private final String baseUrl;
    private final HttpClient http;
    private final Duration tiempoRespuesta;

    public UsuariosHttpClient(String baseUrl) {
        this(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    public UsuariosHttpClient(String baseUrl, Duration tiempoConexion, Duration tiempoRespuesta) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(tiempoConexion).build();
        this.tiempoRespuesta = tiempoRespuesta;
    }

    @Override
    public Socio consultarSocio(Identidad quien, String usuarioId) {
        try {
            UUID.fromString(usuarioId);   // evita construir una URL con contenido inesperado
        } catch (IllegalArgumentException e) {
            throw ApiException.invalida("usuarioId no válido");
        }
        HttpResponse<String> r;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + "/usuarios/" + usuarioId + "/socio"))
                    .timeout(tiempoRespuesta)
                    .header("Accept", "application/json")
                    .header("X-User-Id", quien.usuarioId())
                    .header("X-User-Role", quien.rol())
                    .GET();
            if (quien.token() != null) b.header("Authorization", "Bearer " + quien.token());
            r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {   // incluye conexión rechazada y tiempo de espera agotado
            throw ApiException.noDisponible("El microservicio Usuarios no está disponible");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.noDisponible("El microservicio Usuarios no está disponible");
        } catch (IllegalArgumentException e) {   // URL de Usuarios mal configurada
            throw ApiException.noDisponible("La URL del microservicio Usuarios no es válida");
        }
        if (r.statusCode() == 200) return Socio.desdeJson(r.body());
        if (r.statusCode() == 404) throw ApiException.noEncontrado("El usuario no existe");
        throw ApiException.noDisponible("Usuarios respondió HTTP " + r.statusCode());
    }
}
