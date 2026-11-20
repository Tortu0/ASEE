package com.asee.institucional.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String STAFF = "STAFF";
    private static final String ADMIN = "ADMIN";

    @Bean
    public JwtDecoder jwtDecoder(@Value("${jwt.secret:}") String secreto) {
        if (secreto.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno JWT_SECRET (debe ser el mismo valor que usa el servicio Usuarios)");
        }
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String rol = jwt.getClaimAsString("rol");
            if (rol == null || rol.isBlank()) {
                rol = jwt.getClaimAsString("role");
            }
            if (rol == null || rol.isBlank()) {
                rol = "visitante";
            }
            rol = rol.trim().toUpperCase(Locale.ROOT);
            if (rol.startsWith("ROLE_")) {
                rol = rol.substring(5);
            }
            return List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_" + rol));
        });
        return converter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtDecoder jwtDecoder,
                                           JwtAuthenticationConverter converter) throws Exception {

        AuthenticationEntryPoint noAutenticado = (req, res, ex) ->
                escribirError(res, 401, "Token ausente, inválido o caducado");
        AccessDeniedHandler sinPermiso = (req, res, ex) ->
                escribirError(res, 403, "El rol del usuario no tiene permisos para esta operación");

        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Lecturas públicas
                        .requestMatchers(HttpMethod.GET,
                                "/club/historia", "/club/identidad", "/club/junta",
                                "/club/instalaciones", "/club/palmares", "/club/cantera",
                                "/patrocinadores", "/patrocinadores/*").permitAll()
                        // staff o admin
                        .requestMatchers(HttpMethod.PUT, "/club/historia", "/club/palmares/*")
                        .hasAnyRole(STAFF, ADMIN)
                        .requestMatchers(HttpMethod.POST,
                                "/club/instalaciones", "/club/palmares", "/club/cantera")
                        .hasAnyRole(STAFF, ADMIN)
                        // solo admin
                        .requestMatchers(HttpMethod.PUT,
                                "/club/identidad", "/club/junta", "/patrocinadores/*").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/patrocinadores").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.DELETE,
                                "/club/palmares/*", "/patrocinadores/*").hasRole(ADMIN)
                        // cualquier otra ruta: autenticado
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(noAutenticado)
                        .accessDeniedHandler(sinPermiso))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(noAutenticado)
                        .accessDeniedHandler(sinPermiso));

        return http.build();
    }

    private static void escribirError(HttpServletResponse res, int codigo, String mensaje) throws IOException {
        res.setStatus(codigo);
        res.setContentType("application/json;charset=UTF-8");
        if (codigo == 401) {
            res.setHeader("WWW-Authenticate", "Bearer");
        }
        res.getWriter().write(
                "{\"codigo\":" + codigo + ",\"mensaje\":\"" + mensaje + "\",\"detalles\":[]}");
    }
}