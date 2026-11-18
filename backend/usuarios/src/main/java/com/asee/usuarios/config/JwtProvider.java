package com.asee.usuarios.config;

import com.asee.usuarios.entity.Usuario;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final int jwtExpirationInMs;

    // Spring inyecta automáticamente los valores que pusiste en application.yml
    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") int jwtExpiration) {

        // Convertimos el texto secreto en una clave criptográfica real para el algoritmo HMAC-SHA256
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        // Pasamos los 3600 segundos a milisegundos para trabajar con fechas en Java
        this.jwtExpirationInMs = jwtExpiration * 1000;
    }

    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date fechaExpiracion = new Date(ahora.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .subject(usuario.getId().toString()) // El "sujeto" del token es el ID del usuario
                .claim("email", usuario.getEmail())  // Añadimos datos extra (claims) al payload
                .claim("rol", usuario.getRol())      // Vital para que el API Gateway sepa si es admin o visitante
                .issuedAt(ahora)
                .expiration(fechaExpiracion)
                .signWith(secretKey, Jwts.SIG.HS256) // HS256 explícito: los demás servicios validan HS256
                .compact();
    }
}
