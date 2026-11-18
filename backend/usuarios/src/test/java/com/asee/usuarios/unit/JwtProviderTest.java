package com.asee.usuarios.unit;

import com.asee.usuarios.config.JwtProvider;
import com.asee.usuarios.entity.Rol;
import com.asee.usuarios.entity.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtProviderTest {

    // Un secreto de 64 bytes: sin algoritmo explícito, jjwt firmaría con HS512 y Entradas/Estadísticas (HS256) lo rechazarían
    private static final String SECRETO_LARGO = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void generarToken_SiempreFirmaConHS256_YIncluyeSubjectYRol() {
        Usuario u = new Usuario();
        UUID id = UUID.randomUUID();
        u.setId(id);
        u.setEmail("a@b.com");
        u.setRol(Rol.staff);

        String token = new JwtProvider(SECRETO_LARGO, 3600).generarToken(u);

        var jws = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRETO_LARGO.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token);
        assertEquals("HS256", jws.getHeader().getAlgorithm());
        Claims claims = jws.getPayload();
        assertEquals(id.toString(), claims.getSubject());
        assertEquals("staff", claims.get("rol", String.class));
    }
}
