package com.asee.estadisticas.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Direccion y tiempos de espera del cliente de Deportivo. */
@ConfigurationProperties(prefix = "app.deportivo")
public record DeportivoProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
}
