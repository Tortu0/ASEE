package com.asee.estadisticas.config;

import com.asee.estadisticas.dto.ErrorResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods=false)
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(ResourceLoader resources,@Value("${app.security.public-key:}") String publicKey,
            @Value("${app.security.jwk-set-uri:}") String jwks,@Value("${app.security.issuer}") String issuer,
            @Value("${app.security.audience}") String audience,
            @Value("${app.security.hmac-secret:}") String hmacSecret) throws Exception {
        if (!hmacSecret.isBlank()) {
            // Modo integración con Usuarios: HS256 con el secreto compartido (JWT_SECRET). Usuarios no emite iss ni aud,
            // así que solo se exige firma válida, caducidad y subject UUID. Sin JWT_SECRET se mantiene el modo RS256.
            NimbusJwtDecoder hs = NimbusJwtDecoder.withSecretKey(
                    new SecretKeySpec(hmacSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                    .macAlgorithm(MacAlgorithm.HS256).build();
            OAuth2TokenValidator<Jwt> identidad = jwt -> {
                try {
                    UUID.fromString(jwt.getSubject());
                    if (jwt.getExpiresAt() == null) throw new IllegalArgumentException();
                    return OAuth2TokenValidatorResult.success();
                } catch (RuntimeException ex) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token sin identidad o caducidad validas", null));
                }
            };
            hs.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(30)), identidad));
            return hs;
        }
        NimbusJwtDecoder decoder;
        if (!jwks.isBlank()) decoder=NimbusJwtDecoder.withJwkSetUri(jwks).build();
        else {
            if (publicKey.isBlank()) throw new IllegalStateException("Configura JWT_PUBLIC_KEY o JWT_JWK_SET_URI. Para desarrollo usa scripts/iniciar-estadisticas.ps1");
            String pem;
            try (var input=resources.getResource(publicKey).getInputStream()) { pem=new String(input.readAllBytes(),StandardCharsets.US_ASCII); }
            byte[] der=Base64.getDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----","").replace("-----END PUBLIC KEY-----","").replaceAll("\\s", ""));
            decoder=NimbusJwtDecoder.withPublicKey((RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der))).build();
        }
        OAuth2TokenValidator<Jwt> required=jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                if (jwt.getExpiresAt()==null || !jwt.getAudience().contains(audience)) throw new IllegalArgumentException();
                return OAuth2TokenValidatorResult.success();
            } catch (RuntimeException ex) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Token sin identidad, caducidad o audiencia valida",null));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(30)),new JwtIssuerValidator(issuer),required));
        return decoder;
    }
    @Bean
    SecurityFilterChain security(HttpSecurity http,JsonMapper json) throws Exception {
        JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<String> roles=new HashSet<>();
            for (String claim:List.of("rol","role","roles")) {
                Object value=jwt.getClaims().get(claim);
                if (value instanceof String s) roles.add(s);
                if (value instanceof Collection<?> values) values.stream().filter(String.class::isInstance).map(String.class::cast).forEach(roles::add);
            }
            return roles.stream().filter(r -> Set.of("staff","admin").contains(r))
                    .map(r -> (org.springframework.security.core.GrantedAuthority)new SimpleGrantedAuthority(r)).toList();
        });
        var unauthorized=(org.springframework.security.web.AuthenticationEntryPoint)(request,response,error) -> {
            response.setStatus(401); response.setContentType("application/json"); response.setHeader("WWW-Authenticate","Bearer");
            response.getWriter().write(json.writeValueAsString(new ErrorResponse(401,"Token ausente o no valido",List.of())));
        };
        var forbidden=(org.springframework.security.web.access.AccessDeniedHandler)(request,response,error) -> {
            response.setStatus(403); response.setContentType("application/json");
            response.getWriter().write(json.writeValueAsString(new ErrorResponse(403,"Se requiere rol staff o admin",List.of())));
        };
        return http.csrf(c -> c.disable()).cors(c -> {}).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health/**","/ws","/error").permitAll()
                        .requestMatchers(HttpMethod.GET,"/**").permitAll()
                        .requestMatchers(HttpMethod.PUT,"/partidos/**").hasAnyAuthority("staff","admin")
                        .requestMatchers(HttpMethod.POST,"/partidos/**").hasAnyAuthority("staff","admin")
                        .requestMatchers(HttpMethod.DELETE,"/partidos/**").hasAnyAuthority("staff","admin").anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)).authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
                .build();
    }
    @Bean
    CorsConfigurationSource cors(@Value("${app.allowed-origins}") String origins) {
        var config=new CorsConfiguration(); config.setAllowedOriginPatterns(Arrays.asList(origins.split(",")));
        config.setAllowedMethods(List.of("GET","PUT","POST","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization","Content-Type"));
        var source=new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**",config); return source;
    }
}
