package com.asee.entradas.config;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El decodificador del perfil "dev" NO verifica firma ni caducidad: solo para desarrollo. */
class DevJwtConfigTest {

    private final JwtDecoder decoder = new DevJwtConfig().devJwtDecoder();

    @Test
    void aceptaUnTokenSinFirmaYConservaSusClaims() {
        String token = new PlainJWT(new JWTClaimsSet.Builder()
                .subject("u1").claim("rol", "staff").build()).serialize();

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("u1");
        assertThat(jwt.getClaimAsString("rol")).isEqualTo("staff");
        assertThat(jwt.getTokenValue()).isEqualTo(token);
    }

    @Test
    void sinCaducidad_asignaUnaHoraDeValidez() {
        String token = new PlainJWT(new JWTClaimsSet.Builder().subject("u1").build()).serialize();

        Jwt jwt = decoder.decode(token);

        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofSeconds(3600));
    }

    @Test
    void noVerificaLaCaducidad() {
        Instant iat = Instant.parse("2020-01-01T00:00:00Z");
        Instant exp = Instant.parse("2020-01-01T01:00:00Z");
        String token = new PlainJWT(new JWTClaimsSet.Builder().subject("u1")
                .issueTime(Date.from(iat)).expirationTime(Date.from(exp)).build()).serialize();

        Jwt jwt = decoder.decode(token);   // caducó hace años y aun así se acepta

        assertThat(jwt.getIssuedAt()).isEqualTo(iat);
        assertThat(jwt.getExpiresAt()).isEqualTo(exp);
    }

    @Test
    void descartaElClaimNotBefore() {
        String token = new PlainJWT(new JWTClaimsSet.Builder().subject("u1")
                .notBeforeTime(new Date(System.currentTimeMillis() + 3_600_000)).build()).serialize();

        assertThat(decoder.decode(token).getClaims()).doesNotContainKey("nbf");
    }

    @Test
    void unTokenQueNoEsJwt_lanzaBadJwtException() {
        assertThatThrownBy(() -> decoder.decode("esto-no-es-un-jwt"))
                .isInstanceOf(BadJwtException.class);
    }
}
