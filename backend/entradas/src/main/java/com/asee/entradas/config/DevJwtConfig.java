package com.asee.entradas.config;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.text.ParseException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * SOLO DESARROLLO (perfil "dev"): decodifica el JWT SIN verificar firma ni caducidad.
 *
 * Permite probar Entradas con tokens fabricados a mano mientras no exista el servicio de
 * Usuarios (que es quien emitirá los tokens reales) ni el API Gateway (que los valida).
 * Nunca debe activarse fuera de desarrollo.
 */
@Configuration
@Profile("dev")
public class DevJwtConfig {

    @Bean
    public JwtDecoder devJwtDecoder() {
        return token -> {
            try {
                JWT jwt = JWTParser.parse(token);
                JWTClaimsSet cs = jwt.getJWTClaimsSet();

                Map<String, Object> claims = new HashMap<>(cs.getClaims());
                Instant iat = cs.getIssueTime() != null ? cs.getIssueTime().toInstant() : Instant.now();
                Instant exp = cs.getExpirationTime() != null ? cs.getExpirationTime().toInstant()
                        : iat.plusSeconds(3600);
                claims.put("iat", iat);
                claims.put("exp", exp);
                claims.remove("nbf");

                return new Jwt(token, iat, exp, jwt.getHeader().toJSONObject(), claims);
            } catch (ParseException | RuntimeException e) {
                throw new BadJwtException("Token no válido (modo dev)", e);
            }
        };
    }
}