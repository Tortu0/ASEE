package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Identidad;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida el JWT HS256 que emite Usuarios (mismo secreto JWT_SECRET) usando solo el JDK: firma, caducidad
 * (con 30 s de margen) y subject UUID. Rechaza cualquier otro algoritmo (incluido "none").
 * La firma se comprueba ANTES de leer el contenido, así que el JSON leído es el que firmó Usuarios.
 */
public final class JwtHs256Verifier {

    private static final long MARGEN_SEGUNDOS = 30;
    private static final Pattern ALG = Pattern.compile("\"alg\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SUB = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern ROL = Pattern.compile("\"rol\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    private final byte[] clave;
    private final Clock reloj;

    public JwtHs256Verifier(String secreto, Clock reloj) {
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
        this.reloj = reloj;
    }

    public Identidad verificar(String token) {
        try {
            String[] partes = token.split("\\.", -1);
            if (partes.length != 3) throw invalido();
            Base64.Decoder b64 = Base64.getUrlDecoder();

            String cabecera = new String(b64.decode(partes[0]), StandardCharsets.UTF_8);
            Matcher alg = ALG.matcher(cabecera);
            if (!alg.find() || !"HS256".equals(alg.group(1))) throw invalido();

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            byte[] esperada = mac.doFinal((partes[0] + "." + partes[1]).getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(esperada, b64.decode(partes[2]))) throw invalido();

            String contenido = new String(b64.decode(partes[1]), StandardCharsets.UTF_8);
            Matcher exp = EXP.matcher(contenido);
            if (!exp.find() || reloj.instant().getEpochSecond() > Long.parseLong(exp.group(1)) + MARGEN_SEGUNDOS) {
                throw ApiException.noAutenticado("El token ha caducado");
            }
            Matcher sub = SUB.matcher(contenido);
            if (!sub.find()) throw invalido();
            UUID.fromString(sub.group(1));
            Matcher rol = ROL.matcher(contenido);
            return new Identidad(sub.group(1), rol.find() ? rol.group(1).toLowerCase() : "visitante", token);
        } catch (ApiException e) {
            throw e;
        } catch (IllegalArgumentException | GeneralSecurityException e) {   // base64, UUID o número mal formados
            throw invalido();
        }
    }

    private static ApiException invalido() {
        return ApiException.noAutenticado("Token no válido");
    }
}
