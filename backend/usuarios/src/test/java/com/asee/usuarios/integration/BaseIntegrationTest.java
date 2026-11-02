package com.asee.usuarios.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

    // Definimos el contenedor de PostgreSQL con la misma versión que usas en producción
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("usuarios_test_db")
            .withUsername("admin")
            .withPassword("admin123");

    // Este bloque estático asegura que el contenedor arranque una sola vez para todos los tests
    static {
        postgres.start();
    }

    // Le inyectamos a Spring Boot las credenciales de esta base de datos temporal
    // para que ignore tu application.yml durante los tests.
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Desactivamos RabbitMQ temporalmente en los tests básicos de BBDD
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");

        // Inyectamos una variable de entorno falsa y segura para que el JwtProvider pueda arrancar en los tests
        registry.add("JWT_SECRET", () -> "esta_es_una_clave_secreta_falsa_para_tests_de_mas_de_256_bits");
    }
}