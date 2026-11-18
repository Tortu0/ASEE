package com.asee.compras.aplicacion;

import com.asee.compras.dominio.EstadoPedido;
import com.asee.compras.dominio.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PedidoServicioTest {

    Escenario e;

    @BeforeEach
    void preparar() {
        e = new Escenario();
    }

    static void api(int status, Executable accion) {
        ApiException ex = assertThrows(ApiException.class, accion);
        assertEquals(status, ex.status, "mensaje: " + ex.getMessage());
    }

    Map<String, Object> comprar(String clave) {
        return e.pedidoServicio.crear(e.comprador, clave, e.datos());
    }

    // ───────────── creación: camino feliz ─────────────

    @Test
    void crear_ok_descuentaStock_aplicaDescuentoDeSocio_vaciaCarrito_yGuardaEvento() {
        e.alCarrito(e.comprador, e.camisetaId, "M", 2);   // 2 x 49.90 = 99.80
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);  // 20.00  -> subtotal 119.80, descuento 15 % = 17.97

        Map<String, Object> p = comprar("compra-1");

        assertEquals("pagado", p.get("estado"));
        assertEquals(new BigDecimal("17.97"), p.get("descuentoSocio"));
        assertEquals(new BigDecimal("101.83"), p.get("total"));
        assertEquals(3, e.productos.unidades(e.camisetaId, "M"));
        assertEquals(2, e.productos.unidades(e.gorraId, "unica"));
        assertTrue(e.carritos.obtener(Escenario.COMPRADOR_ID).items.isEmpty(), "el carrito debe vaciarse");
        assertEquals(1, e.pasarela.cobros);

        assertEquals(1, e.publicador.publicados.size(), "el evento 'pedido creado' se publica tras el pago");
        var ev = e.publicador.publicados.get(0);
        assertEquals("pedido.creado", e.publicador.claves.get(0));
        assertEquals(p.get("pedidoId"), ev.referenciaId());
        assertEquals(Escenario.COMPRADOR_ID, ev.usuarioId());
        assertEquals(new BigDecimal("101.83"), ev.importe());
        assertEquals("Pedido de 3 artículos", ev.descripcion());
        assertEquals(PedidoServicio.idEvento((String) p.get("pedidoId")), ev.eventoId(), "el eventoId es estable por pedido");
        assertTrue(e.pedidos.datos.get((String) p.get("pedidoId")).eventoPublicado);
    }

    @Test
    void crear_envia_a_Usuarios_la_identidad_del_comprador() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        comprar("k");
        assertEquals(e.comprador, e.usuarios.ultimaIdentidad);
    }

    @Test
    void crear_sinSerSocio_noAplicaDescuento() {
        e.usuarios.socio = new com.asee.compras.dominio.Socio("no_socio", BigDecimal.ZERO);
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        assertEquals(new BigDecimal("20.00"), comprar("k").get("total"));
    }

    @Test
    void crear_socioCaducado_noAplicaDescuento() {
        e.usuarios.socio = new com.asee.compras.dominio.Socio("caducado", new BigDecimal("15"));
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        assertEquals(new BigDecimal("20.00"), comprar("k").get("total"));
    }

    @Test
    void crear_usaElPrecioActualDelCatalogo_noElDelMomentoDeAnadir() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.productos.datos.get(e.gorraId).precio = new BigDecimal("30.00");
        e.usuarios.socio = new com.asee.compras.dominio.Socio("no_socio", BigDecimal.ZERO);
        assertEquals(new BigDecimal("30.00"), comprar("k").get("total"));
    }

    // ───────────── idempotencia ─────────────

    @Test
    void crear_repetirLaClave_devuelveElMismoPedido_sinCobrarNiDescontarDeNuevo() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        Map<String, Object> primero = comprar("clave-A");
        // el cliente no recibió la respuesta y reintenta; mientras tanto el carrito cambió
        e.alCarrito(e.comprador, e.camisetaId, "L", 1);

        Map<String, Object> segundo = comprar("clave-A");

        assertEquals(primero.get("pedidoId"), segundo.get("pedidoId"));
        assertEquals(primero.get("items"), segundo.get("items"), "conserva los items del primer intento");
        assertEquals(1, e.pasarela.cobros, "no se cobra dos veces");
        assertEquals(2, e.productos.unidades(e.gorraId, "unica"));
        assertEquals(2, e.productos.unidades(e.camisetaId, "L"), "el carrito nuevo no se ha tocado");
        assertEquals(1, e.pedidos.datos.size());
    }

    @Test
    void crear_claveReutilizadaConOtrosDatos_409() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        comprar("clave-A");
        api(409, () -> e.pedidoServicio.crear(e.comprador, "clave-A", new PedidoServicio.DatosPedido("Otra dirección", "tarjeta")));
        api(409, () -> e.pedidoServicio.crear(e.comprador, "clave-A", new PedidoServicio.DatosPedido("Calle Mayor 12, Madroñera", "bizum")));
    }

    @Test
    void crear_mismaClaveEnOtroUsuario_noColisiona() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.alCarrito(e.otro, e.gorraId, "unica", 1);
        assertNotEquals(comprar("clave").get("pedidoId"), e.pedidoServicio.crear(e.otro, "clave", e.datos()).get("pedidoId"));
    }

    @Test
    void crear_elIdDelPedidoEsEstableYEsUnUuid() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        String id = (String) comprar("clave-X").get("pedidoId");
        assertEquals(PedidoServicio.idEstable(Escenario.COMPRADOR_ID, "clave-X"), id);
        assertEquals(id, java.util.UUID.fromString(id).toString());
    }

    // ───────────── validación ─────────────

    @Test
    void crear_validaciones_400_401() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        api(401, () -> e.pedidoServicio.crear(null, "k", e.datos()));
        api(400, () -> e.pedidoServicio.crear(e.comprador, null, e.datos()));
        api(400, () -> e.pedidoServicio.crear(e.comprador, "  ", e.datos()));
        api(400, () -> e.pedidoServicio.crear(e.comprador, "x".repeat(101), e.datos()));
        api(400, () -> e.pedidoServicio.crear(e.comprador, "k", null));
        api(400, () -> e.pedidoServicio.crear(e.comprador, "k", new PedidoServicio.DatosPedido("", "tarjeta")));
        api(400, () -> e.pedidoServicio.crear(e.comprador, "k", new PedidoServicio.DatosPedido("Calle", "efectivo")));
        assertEquals(0, e.pasarela.cobros);
        assertEquals(5, e.productos.unidades(e.camisetaId, "M"));
    }

    @Test
    void crear_claveDeCienCaracteres_esValida() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        assertEquals("pagado", comprar("x".repeat(100)).get("estado"));
    }

    @Test
    void crear_carritoVacio_409() {
        api(409, () -> comprar("k"));
        assertEquals(0, e.usuarios.llamadas, "ni siquiera consulta a Usuarios");
    }

    // ───────────── stock ─────────────

    @Test
    void crear_stockInsuficiente_409_sinCambios() {
        e.alCarrito(e.comprador, e.camisetaId, "L", 2);
        e.productos.datos.get(e.camisetaId).stock.get(1).unidades = 1;   // otro comprador se llevó una

        api(409, () -> comprar("k"));

        assertEquals(1, e.productos.unidades(e.camisetaId, "L"));
        assertTrue(e.pedidos.datos.isEmpty());
        assertEquals(0, e.pasarela.cobros);
        assertFalse(e.carritos.obtener(Escenario.COMPRADOR_ID).items.isEmpty(), "el carrito se conserva");
    }

    @Test
    void crear_productoRetiradoDelCatalogo_409() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.productos.datos.remove(e.gorraId);
        api(409, () -> comprar("k"));
    }

    @Test
    void crear_siFallaUnaLineaPorUnaCarrera_reponeLasAnteriores_yNoDejaPedido() {
        e.alCarrito(e.comprador, e.camisetaId, "M", 2);
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.productos.fallarEnLlamada = 2;   // la validación pasó, pero al descontar la 2.ª línea otro ya se llevó el stock

        api(409, () -> comprar("k"));

        assertEquals(5, e.productos.unidades(e.camisetaId, "M"), "la primera línea se repone");
        assertEquals(3, e.productos.unidades(e.gorraId, "unica"));
        assertTrue(e.pedidos.datos.isEmpty());
        assertEquals(0, e.pasarela.cobros);
    }

    // ───────────── Usuarios ─────────────

    @Test
    void crear_usuariosNoDisponible_503_sinTocarStockNiCobrar() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.usuarios.error = ApiException.noDisponible("Usuarios caído");
        api(503, () -> comprar("k"));
        assertEquals(3, e.productos.unidades(e.gorraId, "unica"));
        assertEquals(0, e.pasarela.cobros);
        assertTrue(e.pedidos.datos.isEmpty());
    }

    @Test
    void crear_usuarioInexistente_404() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.usuarios.error = ApiException.noEncontrado("El usuario no existe");
        api(404, () -> comprar("k"));
        assertEquals(3, e.productos.unidades(e.gorraId, "unica"));
    }

    // ───────────── pago ─────────────

    @Test
    void crear_pagoRechazado_402_reponeStock_noDejaPedido_yPermiteReintentarConLaMismaClave() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 2);
        e.pasarela.alCobrar = ResultadoPago.RECHAZADO;

        api(402, () -> comprar("k"));

        assertEquals(3, e.productos.unidades(e.gorraId, "unica"));
        assertTrue(e.pedidos.datos.isEmpty());
        assertTrue(e.publicador.publicados.isEmpty(), "no hay evento si no hay pago");
        assertFalse(e.carritos.obtener(Escenario.COMPRADOR_ID).items.isEmpty());

        e.pasarela.alCobrar = ResultadoPago.COBRADO;   // el cliente prueba otra tarjeta con la misma clave
        assertEquals("pagado", comprar("k").get("estado"));
    }

    @Test
    void crear_pagoIncierto_503_ydespuesElReintentoLoResuelveSinCobrarDeNuevo() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.pasarela.alCobrar = ResultadoPago.INCIERTO;

        api(503, () -> comprar("k"));
        Pedido pendiente = e.pedidos.datos.values().iterator().next();
        assertEquals(EstadoPedido.PENDIENTE, pendiente.estado);
        assertEquals("incierto", pendiente.pagoEstado);
        assertEquals(2, e.productos.unidades(e.gorraId, "unica"), "el stock sigue retenido");
        assertTrue(e.publicador.publicados.isEmpty());

        e.pasarela.alConsultar = ResultadoPago.COBRADO;   // la pasarela confirma que sí se cobró
        Map<String, Object> p = comprar("k");

        assertEquals("pagado", p.get("estado"));
        assertEquals(1, e.pasarela.cobros, "se consulta antes de repetir el cobro: no se cobra otra vez");
        assertEquals(1, e.pasarela.consultas);
        assertEquals(2, e.productos.unidades(e.gorraId, "unica"), "el stock no se descuenta dos veces");
        assertEquals(1, e.publicador.publicados.size());
        assertTrue(e.carritos.obtener(Escenario.COMPRADOR_ID).items.isEmpty());
    }

    @Test
    void crear_pagoIncierto_siLaPasarelaNoConoceLaReferencia_seRepiteElCobro() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.pasarela.alCobrar = ResultadoPago.INCIERTO;
        api(503, () -> comprar("k"));

        e.pasarela.alConsultar = ResultadoPago.DESCONOCIDO;
        e.pasarela.alCobrar = ResultadoPago.COBRADO;
        assertEquals("pagado", comprar("k").get("estado"));
        assertEquals(2, e.pasarela.cobros);
        assertEquals(2, e.productos.unidades(e.gorraId, "unica"));
        assertEquals(1, new java.util.HashSet<>(e.pasarela.referencias).size(), "misma referencia de cobro en ambos intentos");
    }

    @Test
    void crear_pagoIncierto_siLaPasarelaSigueSinResponder_seguimosEn503() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.pasarela.alCobrar = ResultadoPago.INCIERTO;
        api(503, () -> comprar("k"));
        e.pasarela.consultaLanza = true;
        api(503, () -> comprar("k"));
        assertEquals(1, e.pasarela.cobros);
        assertEquals(EstadoPedido.PENDIENTE, e.pedidos.datos.values().iterator().next().estado);
    }

    @Test
    void crear_pagoEnCurso_409_hastaQuePasaElTiempoMaximo() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.pasarela.alCobrar = ResultadoPago.INCIERTO;
        api(503, () -> comprar("k"));
        // simulamos que el pedido sigue "en curso" (otra petición lo está procesando ahora mismo)
        Pedido p = e.pedidos.datos.values().iterator().next();
        p.pagoEstado = "en_curso";
        p.fechaPedido = e.reloj.instant();

        api(409, () -> comprar("k"));

        e.reloj.avanzarSegundos(61);      // pasó el máximo: se considera abandonado y se consulta a la pasarela
        e.pasarela.alConsultar = ResultadoPago.COBRADO;
        assertEquals("pagado", comprar("k").get("estado"));
    }

    @Test
    void crear_pedidoACiegasSinStockDescontado_requiereConciliacion_503() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.pasarela.alCobrar = ResultadoPago.INCIERTO;
        api(503, () -> comprar("k"));
        e.pedidos.datos.values().iterator().next().stockDescontado = false;   // el proceso murió a mitad del descuento
        api(503, () -> comprar("k"));
        assertEquals(1, e.pasarela.cobros);
    }

    @Test
    void crear_siLaPasarelaLanzaUnaExcepcion_seTrataComoIncierto() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        PedidoServicio servicio = new PedidoServicio(e.productos, e.carritos, e.pedidos, e.usuarios, new PasarelaQueFalla(),
                e.publicador, e.reloj, java.time.Duration.ofSeconds(60));
        api(503, () -> servicio.crear(e.comprador, "k", e.datos()));
        assertEquals(EstadoPedido.PENDIENTE, e.pedidos.datos.values().iterator().next().estado);
        assertEquals(2, e.productos.unidades(e.gorraId, "unica"));
    }

    static final class PasarelaQueFalla implements PasarelaPago {
        @Override public ResultadoPago cobrar(String r, BigDecimal i, String m) { throw new IllegalStateException("timeout"); }
        @Override public ResultadoPago consultar(String r) { throw new IllegalStateException("timeout"); }
        @Override public boolean reembolsar(String r, BigDecimal i) { throw new IllegalStateException("timeout"); }
    }

    // ───────────── consultas ─────────────

    @Test
    void listar_cadaUsuarioVeSusPedidos_staffVeTodos_yFiltra() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        comprar("a");
        e.alCarrito(e.otro, e.gorraId, "unica", 1);
        e.pedidoServicio.crear(e.otro, "b", e.datos());

        assertEquals(1, e.pedidoServicio.listar(e.comprador, null, null).size());
        assertEquals(1, e.pedidoServicio.listar(e.comprador, Escenario.COMPRADOR_ID, null).size());
        api(403, () -> e.pedidoServicio.listar(e.comprador, Escenario.OTRO_ID, null));
        assertEquals(2, e.pedidoServicio.listar(e.staff, null, null).size());
        assertEquals(1, e.pedidoServicio.listar(e.staff, Escenario.OTRO_ID, null).size());
        assertEquals(2, e.pedidoServicio.listar(e.admin, null, "pagado").size());
        assertEquals(0, e.pedidoServicio.listar(e.admin, null, "enviado").size());
        api(400, () -> e.pedidoServicio.listar(e.admin, null, "inventado"));
        api(400, () -> e.pedidoServicio.listar(e.admin, "no-es-uuid", null));
        api(401, () -> e.pedidoServicio.listar(null, null, null));
    }

    @Test
    void obtener_propietarioOStaff_404_403() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        String id = (String) comprar("a").get("pedidoId");
        assertEquals(id, e.pedidoServicio.obtener(e.comprador, id).get("pedidoId"));
        assertEquals(id, e.pedidoServicio.obtener(e.staff, id).get("pedidoId"));
        api(403, () -> e.pedidoServicio.obtener(e.otro, id));
        api(404, () -> e.pedidoServicio.obtener(e.comprador, "no-existe"));
    }

    // ───────────── cancelación ─────────────

    @Test
    void cancelar_pagado_reponeStockUnaSolaVez_yReembolsa() {
        e.alCarrito(e.comprador, e.camisetaId, "M", 2);
        String id = (String) comprar("a").get("pedidoId");
        assertEquals(3, e.productos.unidades(e.camisetaId, "M"));

        e.pedidoServicio.cancelar(e.comprador, id);

        assertEquals(5, e.productos.unidades(e.camisetaId, "M"));
        assertEquals(EstadoPedido.CANCELADO, e.pedidos.datos.get(id).estado);
        assertEquals("completado", e.pedidos.datos.get(id).reembolsoEstado);
        assertEquals(1, e.pasarela.reembolsos);

        e.pedidoServicio.cancelar(e.comprador, id);   // repetir devuelve éxito sin reponer otra vez
        assertEquals(5, e.productos.unidades(e.camisetaId, "M"));
        assertEquals(1, e.pasarela.reembolsos);
    }

    @Test
    void cancelar_reembolsoFallido_quedaPendiente_yUnReintentoLoCompleta() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        String id = (String) comprar("a").get("pedidoId");
        e.pasarela.reembolsoOk = false;

        e.pedidoServicio.cancelar(e.comprador, id);   // 204 aunque el reembolso quede pendiente de conciliación
        assertEquals("pendiente", e.pedidos.datos.get(id).reembolsoEstado);
        assertEquals(3, e.productos.unidades(e.gorraId, "unica"));

        e.pasarela.reembolsoOk = true;
        e.pedidoServicio.cancelar(e.comprador, id);
        assertEquals("completado", e.pedidos.datos.get(id).reembolsoEstado);
        assertEquals(3, e.productos.unidades(e.gorraId, "unica"), "el stock no se repone por segunda vez");
    }

    @Test
    void cancelar_permisos_y_estados() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        String id = (String) comprar("a").get("pedidoId");
        api(403, () -> e.pedidoServicio.cancelar(e.otro, id));
        api(404, () -> e.pedidoServicio.cancelar(e.comprador, "no-existe"));
        api(401, () -> e.pedidoServicio.cancelar(null, id));

        e.pedidoServicio.cambiarEstado(e.staff, id, "preparando");
        e.pedidoServicio.cancelar(e.staff, id);   // staff puede cancelar el de otro, y "preparando" aún es cancelable
        assertEquals(EstadoPedido.CANCELADO, e.pedidos.datos.get(id).estado);
    }

    @Test
    void cancelar_enviadoOEntregado_409() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        String id = (String) comprar("a").get("pedidoId");
        e.pedidoServicio.cambiarEstado(e.staff, id, "preparando");
        e.pedidoServicio.cambiarEstado(e.staff, id, "enviado");
        api(409, () -> e.pedidoServicio.cancelar(e.comprador, id));
        e.pedidoServicio.cambiarEstado(e.staff, id, "entregado");
        api(409, () -> e.pedidoServicio.cancelar(e.comprador, id));
        assertEquals(2, e.productos.unidades(e.gorraId, "unica"));
    }

    @Test
    void cancelar_conPagoEnCurso_409() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.pasarela.alCobrar = ResultadoPago.INCIERTO;
        api(503, () -> comprar("a"));
        String id = e.pedidos.datos.keySet().iterator().next();
        api(409, () -> e.pedidoServicio.cancelar(e.comprador, id));
    }

    @Test
    void cancelar_laClaveDeUnPedidoCancelado_noSePuedeReutilizar() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        String id = (String) comprar("a").get("pedidoId");
        e.pedidoServicio.cancelar(e.comprador, id);
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        api(409, () -> comprar("a"));
        assertEquals("pagado", comprar("b").get("estado"));
    }

    // ───────────── cambio de estado ─────────────

    @Test
    void cambiarEstado_flujoValido_repetirEsOk_y_resto409() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        String id = (String) comprar("a").get("pedidoId");

        assertEquals("pagado", e.pedidoServicio.cambiarEstado(e.staff, id, "pagado").get("estado"));   // repetir el actual
        api(409, () -> e.pedidoServicio.cambiarEstado(e.staff, id, "enviado"));                        // saltarse un paso
        assertEquals("preparando", e.pedidoServicio.cambiarEstado(e.staff, id, "preparando").get("estado"));
        api(409, () -> e.pedidoServicio.cambiarEstado(e.staff, id, "pagado"));                         // hacia atrás
        api(409, () -> e.pedidoServicio.cambiarEstado(e.staff, id, "cancelado"));                      // se cancela con DELETE
        api(409, () -> e.pedidoServicio.cambiarEstado(e.staff, id, "pendiente"));
        assertEquals("enviado", e.pedidoServicio.cambiarEstado(e.admin, id, "enviado").get("estado"));
        assertEquals("entregado", e.pedidoServicio.cambiarEstado(e.staff, id, "entregado").get("estado"));
        api(409, () -> e.pedidoServicio.cambiarEstado(e.staff, id, "preparando"));
    }

    @Test
    void cambiarEstado_permisos_y_validacion() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        String id = (String) comprar("a").get("pedidoId");
        api(403, () -> e.pedidoServicio.cambiarEstado(e.comprador, id, "preparando"));
        api(401, () -> e.pedidoServicio.cambiarEstado(null, id, "preparando"));
        api(400, () -> e.pedidoServicio.cambiarEstado(e.staff, id, "volando"));
        api(400, () -> e.pedidoServicio.cambiarEstado(e.staff, id, null));
        api(404, () -> e.pedidoServicio.cambiarEstado(e.staff, "no-existe", "preparando"));
    }

    // ───────────── eventos ─────────────

    @Test
    void eventos_siElBrokerFalla_elPedidoSeConfirma_yElPublicadorReintenta_sinDuplicar() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.publicador.fallar = true;

        Map<String, Object> p = comprar("a");   // el pedido se confirma igualmente

        assertEquals("pagado", p.get("estado"));
        assertTrue(e.publicador.publicados.isEmpty());
        assertFalse(e.pedidos.datos.get((String) p.get("pedidoId")).eventoPublicado);
        assertEquals(0, e.pedidoServicio.publicarPendientes(), "sigue caído");

        e.publicador.fallar = false;
        assertEquals(1, e.pedidoServicio.publicarPendientes());
        assertEquals(1, e.publicador.publicados.size());
        assertEquals(0, e.pedidoServicio.publicarPendientes(), "ya no queda nada pendiente");
        assertEquals(1, e.publicador.publicados.size());
    }

    @Test
    void eventos_unPedidoPendienteDePago_noPublicaEvento() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.pasarela.alCobrar = ResultadoPago.INCIERTO;
        api(503, () -> comprar("a"));
        assertEquals(0, e.pedidoServicio.publicarPendientes());
        assertTrue(e.publicador.publicados.isEmpty());
    }
}
