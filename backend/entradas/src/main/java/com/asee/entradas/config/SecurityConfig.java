package com.asee.entradas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración de Spring Security.
 *
 * - Los endpoints de consulta de asientos, aforo y precios son públicos.
 * - El resto requiere un JWT válido (validado por el API Gateway, pero también
 *   verificado aquí como segunda línea de defensa).
 * - El modo es stateless (sin sesión HTTP).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())   // CORS: ver CorsConfig (solo perfil dev)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos: mapa de asientos, aforo, precios
                        .requestMatchers(HttpMethod.GET, "/partidos/*/asientos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/partidos/*/aforo").permitAll()
                        .requestMatchers(HttpMethod.GET, "/partidos/*/precios").permitAll()
                        // Todo lo demás requiere autenticación
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> {}) // Valida token JWT
                );
        return http.build();
    }
}