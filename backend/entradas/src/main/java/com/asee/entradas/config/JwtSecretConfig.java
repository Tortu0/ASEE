package com.asee.entradas.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Valida los JWT que emite Usuarios: HS256 con el secreto compartido (variable JWT_SECRET).
 * Comprueba firma y caducidad. Sustituye al decodificador por issuer-uri (que apuntaba a un Keycloak
 * inexistente) y evita usar el perfil "dev", que no verifica nada.
 * Solo se activa si JWT_SECRET está definido y el perfil "dev" no está activo.
 */
@Configuration
@Profile("!dev")
@ConditionalOnExpression("!'${jwt.secret:}'.isEmpty()")
public class JwtSecretConfig {

    @Bean
    public JwtDecoder hmacJwtDecoder(@Value("${jwt.secret}") String secret) {
        return NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
