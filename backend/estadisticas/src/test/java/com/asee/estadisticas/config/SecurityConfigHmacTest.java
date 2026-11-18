package com.asee.estadisticas.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Modo HS256 (JWT_SECRET): acepta los tokens de Usuarios, que no llevan issuer ni audience. */
class SecurityConfigHmacTest {

    private static final String SECRETO = "secreto-compartido-con-usuarios-de-al-menos-32-bytes";
    private static final String UUID_OK = "22222222-2222-2222-2222-222222222222";

    private JwtDecoder decoder() throws Exception {
        return new SecurityConfig().jwtDecoder(null, "", "", "asee-local", "estadisticas", SECRETO);
    }

    private static String token(String sub, String secreto, long caducaEnMs) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject(sub).claim("rol", "staff")
                .issueTime(new Date()).expirationTime(new Date(System.currentTimeMillis() + caducaEnMs)).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @Test
    void aceptaTokenDeUsuariosSinIssuerNiAudience() throws Exception {
        Jwt jwt = decoder().decode(token(UUID_OK, SECRETO, 60_000));
        assertEquals("staff", jwt.getClaimAsString("rol"));
    }

    @Test
    void rechazaFirmaFalsa() throws Exception {
        String falso = token(UUID_OK, "otro-secreto-distinto-de-al-menos-32-bytes!!", 60_000);
        assertThrows(JwtException.class, () -> decoder().decode(falso));
    }

    @Test
    void rechazaSubjectQueNoEsUuid() throws Exception {
        String t = token("no-es-uuid", SECRETO, 60_000);
        assertThrows(JwtException.class, () -> decoder().decode(t));
    }

    @Test
    void rechazaTokenCaducado() throws Exception {
        String t = token(UUID_OK, SECRETO, -120_000);
        assertThrows(JwtException.class, () -> decoder().decode(t));
    }
}
