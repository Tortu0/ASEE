package com.asee.compras.infraestructura;

import com.asee.compras.aplicacion.ApiException;
import com.asee.compras.aplicacion.ResultadoPago;
import com.asee.compras.dominio.Identidad;
import com.asee.compras.dominio.Socio;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InfraestructuraTest {

    static final String UID = "11111111-1111-4111-8111-111111111111";
    HttpServer servidor;
    String base;
    final AtomicReference<com.sun.net.httpserver.Headers> cabeceras = new AtomicReference<>();
    final AtomicReference<String> ruta = new AtomicReference<>();
    volatile int estado = 200;
    volatile String cuerpo = "{\"estado\":\"activo\",\"descuentoPorcentaje\":15}";
    volatile long retrasoMs = 0;

    @BeforeEach
    void arrancar() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", ex -> {
            cabeceras.set(ex.getRequestHeaders());
            ruta.set(ex.getRequestURI().getPath());
            try { Thread.sleep(retrasoMs); } catch (InterruptedException ignorada) { }
            byte[] b = cuerpo.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(estado, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void parar() { servidor.stop(0); }

    static void api(int status, org.junit.jupiter.api.function.Executable e) {
        assertEquals(status, assertThrows(ApiException.class, e).status);
    }

    // ── cliente de Usuarios ──

    @Test
    void consultarSocio_200_leeElDescuento_yReenviaIdentidadYBearer() {
        Socio s = new UsuariosHttpClient(base).consultarSocio(new Identidad(UID, "socio", "mi-token"), UID);
        assertTrue(s.activo());
        assertEquals(new BigDecimal("15"), s.descuentoAplicable());
        assertEquals("/v1/usuarios/" + UID + "/socio", ruta.get());
        assertEquals(UID, cabeceras.get().getFirst("X-User-Id"));
        assertEquals("socio", cabeceras.get().getFirst("X-User-Role"));
        assertEquals("Bearer mi-token", cabeceras.get().getFirst("Authorization"));
    }

    @Test
    void consultarSocio_sinToken_noEnviaAuthorization() {
        new UsuariosHttpClient(base + "/").consultarSocio(new Identidad(UID, "socio", null), UID);
        assertNull(cabeceras.get().getFirst("Authorization"));
        assertEquals("/v1/usuarios/" + UID + "/socio", ruta.get(), "tolera una barra final en la URL base");
    }

    @Test
    void consultarSocio_404_usuarioNoExiste() {
        estado = 404;
        api(404, () -> new UsuariosHttpClient(base).consultarSocio(new Identidad(UID, "socio", null), UID));
    }

    @Test
    void consultarSocio_errorDeServidor_o_403_es503() {
        for (int codigo : new int[] { 500, 502, 503, 403, 400 }) {
            estado = codigo;
            api(503, () -> new UsuariosHttpClient(base).consultarSocio(new Identidad(UID, "socio", null), UID));
        }
    }

    @Test
    void consultarSocio_tiempoAgotado_es503() {
        retrasoMs = 1500;
        var cliente = new UsuariosHttpClient(base, Duration.ofSeconds(1), Duration.ofMillis(300));
        long t0 = System.nanoTime();
        api(503, () -> cliente.consultarSocio(new Identidad(UID, "socio", null), UID));
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 1400, "no espera a la respuesta lenta");
    }

    @Test
    void consultarSocio_servidorCaido_es503() {
        int puerto = servidor.getAddress().getPort();
        servidor.stop(0);
        api(503, () -> new UsuariosHttpClient("http://127.0.0.1:" + puerto + "/v1").consultarSocio(new Identidad(UID, "socio", null), UID));
    }

    @Test
    void consultarSocio_urlMalConfigurada_es503_yUsuarioIdInvalido_es400() {
        api(503, () -> new UsuariosHttpClient("esto no es una url").consultarSocio(new Identidad(UID, "socio", null), UID));
        api(400, () -> new UsuariosHttpClient(base).consultarSocio(new Identidad(UID, "socio", null), "../../etc/passwd"));
    }

    // ── pasarela simulada ──

    @Test
    void pasarela_ok_esIdempotentePorReferencia() {
        var p = new PasarelaSimulada("ok");
        assertEquals(ResultadoPago.DESCONOCIDO, p.consultar("R1"));
        assertEquals(ResultadoPago.COBRADO, p.cobrar("R1", BigDecimal.TEN, "tarjeta"));
        assertEquals(ResultadoPago.COBRADO, p.cobrar("R1", BigDecimal.TEN, "tarjeta"));
        assertEquals(ResultadoPago.COBRADO, p.consultar("R1"));
        assertTrue(p.reembolsar("R1", BigDecimal.TEN));
        assertFalse(p.reembolsar("R-desconocida", BigDecimal.TEN));
    }

    @Test
    void pasarela_rechazado_y_incierto() {
        var r = new PasarelaSimulada("rechazado");
        assertEquals(ResultadoPago.RECHAZADO, r.cobrar("R1", BigDecimal.TEN, "bizum"));
        assertEquals(ResultadoPago.RECHAZADO, r.consultar("R1"));
        assertFalse(r.reembolsar("R1", BigDecimal.TEN));

        var i = new PasarelaSimulada("incierto");
        assertEquals(ResultadoPago.INCIERTO, i.cobrar("R2", BigDecimal.TEN, "paypal"));
        assertEquals(ResultadoPago.COBRADO, i.consultar("R2"), "el cobro sí se hizo; solo se perdió la respuesta");
        assertEquals(ResultadoPago.COBRADO, new PasarelaSimulada(null).cobrar("R3", BigDecimal.ONE, "tarjeta"));
    }
}
