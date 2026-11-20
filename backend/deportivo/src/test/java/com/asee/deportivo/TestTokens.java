package com.asee.deportivo;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

// Fabrica JWT HS256 como los de Usuarios, para probar la seguridad sin levantar otros servicios
public final class TestTokens {

    public static final String SECRETO = "01234567891011121314151617181920";

    private TestTokens() {
    }

    public static String token(String sub, String rol, String secreto, long segundos) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(sub)
                    .claim("rol", rol)
                    .expirationTime(Date.from(Instant.now().plusSeconds(segundos)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secreto.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String bearer(String sub, String rol) {
        return "Bearer " + token(sub, rol, SECRETO, 3600);
    }
}