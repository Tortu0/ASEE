package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Identidad;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.asee.compras.aplicacion.PedidoServicioTest.api;
import static org.junit.jupiter.api.Assertions.*;

class AutenticacionTest {

    static final String SECRETO = "secreto-compartido-con-usuarios-de-al-menos-32-bytes";
    static final String UID = "55555555-5555-4555-8555-555555555555";
    final Fakes.RelojManual reloj = new Fakes.RelojManual();

    static String b64(String s) { return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8)); }

    static String firmar(String datos, String secreto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(datos.getBytes(StandardCharsets.US_ASCII)));
    }

    /** Token con el mismo aspecto que el de Usuarios (jjwt): {"alg":"HS256"} y {"email","rol","sub","iat","exp"}. */
    String token(String alg, String sub, String rol, long exp, String secreto) throws Exception {
        String cabecera = b64("{\"alg\":\"" + alg + "\"}");
        String contenido = b64("{\"email\":\"a@b.com\",\"rol\":\"" + rol + "\",\"sub\":\"" + sub + "\",\"iat\":1700000000,\"exp\":" + exp + "}");
        return cabecera + "." + contenido + "." + firmar(cabecera + "." + contenido, secreto);
    }

    long enUnaHora() { return reloj.instant().getEpochSecond() + 3600; }

    // ── verificador JWT ──

    @Test
    void jwt_valido_devuelveIdentidadConRol() throws Exception {
        Identidad i = new JwtHs256Verifier(SECRETO, reloj).verificar(token("HS256", UID, "STAFF", enUnaHora(), SECRETO));
        assertEquals(UID, i.usuarioId());
        assertEquals("staff", i.rol());
        assertTrue(i.esStaff());
        assertNotNull(i.token());
    }

    @Test
    void jwt_rechazos_401() throws Exception {
        JwtHs256Verifier v = new JwtHs256Verifier(SECRETO, reloj);
        api(401, () -> v.verificar(token("HS256", UID, "admin", enUnaHora(), "otro-secreto-distinto-de-al-menos-32-bytes")));     // firma falsa
        api(401, () -> v.verificar(token("HS256", UID, "admin", reloj.instant().getEpochSecond() - 120, SECRETO)));                 // caducado
        api(401, () -> v.verificar(token("none", UID, "admin", enUnaHora(), SECRETO)));                                              // alg none
        api(401, () -> v.verificar(token("HS512", UID, "admin", enUnaHora(), SECRETO)));                                             // otro algoritmo
        api(401, () -> v.verificar(token("HS256", "no-es-uuid", "admin", enUnaHora(), SECRETO)));                                    // sub no UUID
        api(401, () -> v.verificar("a.b"));
        api(401, () -> v.verificar("a.b.c.d"));
        api(401, () -> v.verificar("%%%.%%%.%%%"));
        api(401, () -> v.verificar(""));
    }

    @Test
    void jwt_alterarElContenidoInvalidaLaFirma() throws Exception {
        String t = token("HS256", UID, "visitante", enUnaHora(), SECRETO);
        String[] p = t.split("\\.");
        String falso = p[0] + "." + b64("{\"rol\":\"admin\",\"sub\":\"" + UID + "\",\"exp\":" + enUnaHora() + "}") + "." + p[2];
        api(401, () -> new JwtHs256Verifier(SECRETO, reloj).verificar(falso));
    }

    @Test
    void jwt_toleraUnMargenDe30SegundosDeDesfaseDeRelojes() throws Exception {
        JwtHs256Verifier v = new JwtHs256Verifier(SECRETO, reloj);
        assertNotNull(v.verificar(token("HS256", UID, "socio", reloj.instant().getEpochSecond() - 10, SECRETO)));
    }

    @Test
    void jwt_sinClaimRol_esVisitante() throws Exception {
        String c = b64("{\"alg\":\"HS256\"}"), p = b64("{\"sub\":\"" + UID + "\",\"exp\":" + enUnaHora() + "}");
        assertEquals("visitante", new JwtHs256Verifier(SECRETO, reloj).verificar(c + "." + p + "." + firmar(c + "." + p, SECRETO)).rol());
    }

    // ── autenticador ──

    @Test
    void conSecreto_exigeBearer_eIgnoraLasCabecerasDelCliente() throws Exception {
        Autenticador a = new Autenticador(SECRETO, reloj);
        Identidad i = a.resolver("Bearer " + token("HS256", UID, "socio", enUnaHora(), SECRETO), "99999999-9999-4999-8999-999999999999", "admin");
        assertEquals(UID, i.usuarioId());
        assertEquals("socio", i.rol(), "X-User-Role falsificada no cuenta");
        api(401, () -> a.resolver(null, UID, "admin"));
        api(401, () -> a.resolver("Basic abc", UID, "admin"));
        api(401, () -> a.resolver("Bearer ", UID, "admin"));
        api(401, () -> a.resolver("Bearer " + token("HS256", UID, "admin", enUnaHora(), "otro-secreto-distinto-de-al-menos-32-bytes"), null, null));
    }

    @Test
    void sinSecreto_confiaEnLasCabecerasDelGateway() {
        Autenticador a = new Autenticador("", reloj);
        Identidad i = a.resolver("Bearer abc", UID, "Admin");
        assertEquals(UID, i.usuarioId());
        assertEquals("admin", i.rol());
        assertEquals("abc", i.token());
        assertEquals("visitante", a.resolver(null, UID, null).rol());
        api(401, () -> a.resolver(null, null, "admin"));
        api(401, () -> a.resolver(null, " ", "admin"));
        api(401, () -> a.resolver(null, "no-es-uuid", "admin"));
    }
}
