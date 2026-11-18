package com.asee.compras.dominio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DominioTest {

    static ItemCarrito item(String precio, int cantidad) {
        ItemCarrito i = new ItemCarrito();
        i.precioUnitario = new BigDecimal(precio);
        i.cantidad = cantidad;
        return i;
    }

    // ── Calculadora ──

    @Test
    void totales_sinDescuento() {
        var t = Calculadora.totales(List.of(item("49.90", 2), item("20.00", 1)), BigDecimal.ZERO);
        assertEquals(new BigDecimal("119.80"), t.subtotal());
        assertEquals(new BigDecimal("0.00"), t.descuentoSocio());
        assertEquals(new BigDecimal("119.80"), t.total());
    }

    @Test
    void totales_conDescuento_redondeaAcentimos() {
        var t = Calculadora.totales(List.of(item("0.99", 1)), new BigDecimal("15"));   // 0.1485 -> 0.15
        assertEquals(new BigDecimal("0.15"), t.descuentoSocio());
        assertEquals(new BigDecimal("0.84"), t.total());
        var u = Calculadora.totales(List.of(item("10.05", 1)), new BigDecimal("10"));   // 1.005 -> 1.01
        assertEquals(new BigDecimal("1.01"), u.descuentoSocio());
        assertEquals(new BigDecimal("9.04"), u.total());
    }

    @Test
    void totales_descuentoFueraDeRango_seAcota_yNullEsCero() {
        assertEquals(new BigDecimal("0.00"), Calculadora.totales(List.of(item("10", 1)), new BigDecimal("150")).total());
        assertEquals(new BigDecimal("10.00"), Calculadora.totales(List.of(item("10", 1)), new BigDecimal("-5")).total());
        assertEquals(new BigDecimal("10.00"), Calculadora.totales(List.of(item("10", 1)), null).total());
        assertEquals(new BigDecimal("0.00"), Calculadora.totales(List.of(), new BigDecimal("15")).total());
    }

    // ── Flujo de estados ──

    @Test
    void flujoDeEstados() {
        assertTrue(FlujoEstados.permitido(EstadoPedido.PAGADO, EstadoPedido.PREPARANDO));
        assertTrue(FlujoEstados.permitido(EstadoPedido.PREPARANDO, EstadoPedido.ENVIADO));
        assertTrue(FlujoEstados.permitido(EstadoPedido.ENVIADO, EstadoPedido.ENTREGADO));
        for (EstadoPedido e : EstadoPedido.values()) assertTrue(FlujoEstados.permitido(e, e), "repetir " + e);
        assertFalse(FlujoEstados.permitido(EstadoPedido.PAGADO, EstadoPedido.ENVIADO));
        assertFalse(FlujoEstados.permitido(EstadoPedido.PREPARANDO, EstadoPedido.PAGADO));
        assertFalse(FlujoEstados.permitido(EstadoPedido.PENDIENTE, EstadoPedido.PAGADO));
        assertFalse(FlujoEstados.permitido(EstadoPedido.PAGADO, EstadoPedido.CANCELADO));
        assertFalse(FlujoEstados.permitido(EstadoPedido.CANCELADO, EstadoPedido.PAGADO));
        assertFalse(FlujoEstados.permitido(EstadoPedido.ENTREGADO, EstadoPedido.PREPARANDO));
    }

    @Test
    void estadosDelContrato() {
        assertEquals(EstadoPedido.PREPARANDO, EstadoPedido.deValor("preparando"));
        assertNull(EstadoPedido.deValor("PREPARANDO"));
        assertNull(EstadoPedido.deValor(null));
        assertEquals(List.of("pendiente", "pagado", "preparando", "enviado", "entregado", "cancelado"),
                java.util.Arrays.stream(EstadoPedido.values()).map(EstadoPedido::valor).toList());
    }

    // ── Socio ──

    @Test
    void socio_lecturaDelJsonDeUsuarios() {
        Socio s = Socio.desdeJson("{\"usuarioId\":\"x\",\"numeroSocio\":\"S-1\",\"estado\":\"activo\",\"tipoCuota\":\"anual\",\"descuentoPorcentaje\":15}");
        assertTrue(s.activo());
        assertEquals(new BigDecimal("15"), s.descuentoAplicable());
        assertEquals(new BigDecimal("12.5"), Socio.desdeJson("{\"estado\" : \"activo\", \"descuentoPorcentaje\" : 12.5}").descuentoAplicable());
    }

    @Test
    void socio_noActivo_noDaDescuento() {
        assertEquals(BigDecimal.ZERO, Socio.desdeJson("{\"estado\":\"caducado\",\"descuentoPorcentaje\":15}").descuentoAplicable());
        assertEquals(BigDecimal.ZERO, Socio.desdeJson("{\"estado\":\"no_socio\",\"descuentoPorcentaje\":0}").descuentoAplicable());
        assertEquals(BigDecimal.ZERO, Socio.desdeJson("{}").descuentoAplicable());
        assertEquals(BigDecimal.ZERO, Socio.desdeJson(null).descuentoAplicable());
    }

    // ── Vistas ──

    @Test
    void vistaPedido_usaLosNombresDelContrato() {
        Pedido p = new Pedido();
        p.id = "id"; p.usuarioId = "u"; p.estado = EstadoPedido.PAGADO; p.total = BigDecimal.TEN; p.descuentoSocio = BigDecimal.ONE;
        p.direccionEnvio = "d"; p.metodoPago = "tarjeta"; p.fechaPedido = java.time.Instant.parse("2026-11-12T10:00:00Z");
        var v = Vistas.pedido(p);
        assertEquals(List.of("pedidoId", "usuarioId", "items", "total", "descuentoSocio", "direccionEnvio", "metodoPago", "estado", "fechaPedido"),
                List.copyOf(v.keySet()));
        assertEquals("pagado", v.get("estado"));
        assertEquals("2026-11-12T10:00:00Z", v.get("fechaPedido"));
    }
}
