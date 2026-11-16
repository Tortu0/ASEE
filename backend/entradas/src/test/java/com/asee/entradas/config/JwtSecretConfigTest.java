package com.asee.entradas.config;

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

class JwtSecretConfigTest {

    private static final String SECRETO = "secreto-compartido-con-usuarios-de-al-menos-32-bytes";

    private final JwtDecoder decoder = new JwtSecretConfig().hmacJwtDecoder(SECRETO);

    private static String token(String secreto, Date caduca) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("11111111-1111-1111-1111-111111111111").claim("rol", "socio")
                .issueTime(new Date()).expirationTime(caduca).build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @Test
    void aceptaUnTokenFirmadoPorUsuarios() throws Exception {
        Jwt jwt = decoder.decode(token(SECRETO, new Date(System.currentTimeMillis() + 60_000)));
        assertEquals("socio", jwt.getClaimAsString("rol"));
    }

    @Test
    void rechazaUnaFirmaFalsa() throws Exception {
        String falso = token("otro-secreto-distinto-de-al-menos-32-bytes!!", new Date(System.currentTimeMillis() + 60_000));
        assertThrows(JwtException.class, () -> decoder.decode(falso));
    }

    @Test
    void rechazaUnTokenCaducado() throws Exception {
        String caducado = token(SECRETO, new Date(System.currentTimeMillis() - 120_000));
        assertThrows(JwtException.class, () -> decoder.decode(caducado));
    }
}
