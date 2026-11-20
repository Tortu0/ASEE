package com.asee.deportivo.config;

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

    // Valida los JWT que emite Usuarios: HS256 con el secreto compartido (JWT_SECRET).
    // Comprueba firma y caducidad. Si falta el secreto, el servicio NO arranca.
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

    // Convierte el claim "rol" del token (visitante, socio, staff, admin) en el permiso ROLE_<ROL>
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

        // Mensajes del YAML (respuestas Unauthorized y Forbidden), en el formato Error
        AuthenticationEntryPoint noAutenticado = (req, res, ex) ->
                escribirError(res, 401, "Token ausente, inválido o caducado");
        AccessDeniedHandler sinPermiso = (req, res, ex) ->
                escribirError(res, 403, "El rol del usuario no tiene permisos para esta operación");

        http
                .cors(Customizer.withDefaults())   // usa el CorsConfig de desarrollo
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Lecturas públicas: las usan el front, Entradas y Estadísticas
                        .requestMatchers(HttpMethod.GET,
                                "/partidos", "/partidos/*",
                                "/jugadores", "/jugadores/*",
                                "/noticias", "/noticias/*").permitAll()
                        // Crear y editar: staff o admin
                        .requestMatchers(HttpMethod.POST,
                                "/partidos", "/jugadores", "/noticias").hasAnyRole(STAFF, ADMIN)
                        .requestMatchers(HttpMethod.PUT,
                                "/partidos/*", "/jugadores/*", "/noticias/*").hasAnyRole(STAFF, ADMIN)
                        .requestMatchers(HttpMethod.DELETE, "/noticias/*").hasAnyRole(STAFF, ADMIN)
                        // Borrar partidos y jugadores: solo admin
                        .requestMatchers(HttpMethod.DELETE, "/partidos/*", "/jugadores/*").hasRole(ADMIN)
                        // Preferencias: usuario autenticado (el propietario se comprueba en el controlador)
                        .requestMatchers("/usuarios/*/preferencias-notificacion").authenticated()
                        // Cualquier otra ruta: hay que estar autenticado
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