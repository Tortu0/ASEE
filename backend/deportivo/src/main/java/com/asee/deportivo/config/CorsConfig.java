package com.asee.deportivo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS solo para desarrollo (app.cors-dev=true): permite que el frontend, servido desde
 * http://localhost:<puerto>, llame a este servicio desde el navegador.
 * En producción esto lo resuelve el API Gateway.
 */
@Configuration
@ConditionalOnProperty(name = "app.cors-dev", havingValue = "true")
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}