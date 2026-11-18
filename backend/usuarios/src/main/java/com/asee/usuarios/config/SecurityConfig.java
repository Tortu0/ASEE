package com.asee.usuarios.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Le decimos a Spring que cuando necesite encriptar algo, use Argon2id
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Los parámetros representan: tamaño de la sal, longitud del hash, paralelismo, memoria (KB) e iteraciones.
        // Estos son valores seguros recomendados para aplicaciones modernas.
        return new Argon2PasswordEncoder(16, 32, 1, 16384, 2);
    }

    // Desactivamos la seguridad web clásica de Spring
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Desactivamos CSRF porque nuestra API usará tokens JWT (stateless), no cookies de sesión
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Dejamos pasar todas las peticiones a nivel de microservicio local.
                        // ¿Por qué? Porque el encargado de bloquear a los usuarios sin autorización
                        // será el API Gateway antes de que la petición llegue aquí.
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}