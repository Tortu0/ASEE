package com.asee.entradas.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configura los {@link WebClient} utilizados para las llamadas síncronas a
 * los microservicios Deportivo y Usuarios.
 */
@Configuration
public class WebClientConfig {

    @Value("${clientes.deportivo.url}")
    private String deportivoUrl;

    @Value("${clientes.usuarios.url}")
    private String usuariosUrl;

    @Bean("deportivoWebClient")
    public WebClient deportivoClient() {
        return WebClient.builder()
                .baseUrl(deportivoUrl)
                .build();
    }

    @Bean("usuariosWebClient")
    public WebClient usuariosClient() {
        return WebClient.builder()
                .baseUrl(usuariosUrl)
                .build();
    }
}
