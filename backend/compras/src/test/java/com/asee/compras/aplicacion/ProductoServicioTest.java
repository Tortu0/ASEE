package com.asee.compras.aplicacion;

import com.asee.compras.aplicacion.ProductoServicio.DatosProducto;
import com.asee.compras.dominio.StockTalla;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.asee.compras.aplicacion.PedidoServicioTest.api;
import static org.junit.jupiter.api.Assertions.*;

class ProductoServicioTest {

    Escenario e;

    @BeforeEach
    void preparar() { e = new Escenario(); }

    DatosProducto datos(String nombre, String categoria, String precio, List<StockTalla> stock) {
        return new DatosProducto(nombre, "desc", categoria, precio == null ? null : new BigDecimal(precio), stock, List.of("http://f/1.png"), null, null);
    }

    @Test
    void crear_staffYAdmin_devuelveElProductoConSuId() {
        Map<String, Object> p = e.productoServicio.crear(e.staff, datos("Bufanda", "bufanda", "14.5", List.of(new StockTalla("unica", 10))));
        assertEquals("Bufanda", p.get("nombre"));
        assertEquals(new BigDecimal("14.50"), p.get("precio"));
        assertNotNull(p.get("productoId"));
        assertEquals(p, e.productoServicio.obtener((String) p.get("productoId")));
        assertNotNull(e.productoServicio.crear(e.admin, datos("X", "otro", "0", null)));   // sin stock y precio 0 son válidos
    }

    @Test
    void crear_permisos() {
        api(401, () -> e.productoServicio.crear(null, datos("X", "otro", "1", null)));
        api(403, () -> e.productoServicio.crear(e.comprador, datos("X", "otro", "1", null)));
    }

    @Test
    void crear_validaciones_400() {
        api(400, () -> e.productoServicio.crear(e.staff, null));
        api(400, () -> e.productoServicio.crear(e.staff, datos(" ", "otro", "1", null)));
        api(400, () -> e.productoServicio.crear(e.staff, datos("X", "zapatillas", "1", null)));
        api(400, () -> e.productoServicio.crear(e.staff, datos("X", null, "1", null)));
        api(400, () -> e.productoServicio.crear(e.staff, datos("X", "otro", null, null)));
        api(400, () -> e.productoServicio.crear(e.staff, datos("X", "otro", "-1", null)));
        api(400, () -> e.productoServicio.crear(e.staff, datos("X", "otro", "1", List.of(new StockTalla("M", -1)))));
        api(400, () -> e.productoServicio.crear(e.staff, datos("X", "otro", "1", List.of(new StockTalla("M", 1), new StockTalla("M", 2)))));
        api(400, () -> e.productoServicio.crear(e.staff, datos("X", "otro", "1", List.of(new StockTalla("", 1)))));
        api(400, () -> e.productoServicio.crear(e.staff, new DatosProducto("X", null, "otro", BigDecimal.ONE, null, null, "no-uuid", null)));
    }

    @Test
    void actualizar_reemplazaLosDatos_y404SiNoExiste() {
        Map<String, Object> p = e.productoServicio.actualizar(e.staff, e.gorraId, datos("Gorra v2", "gorra", "25", List.of(new StockTalla("unica", 7))));
        assertEquals(e.gorraId, p.get("productoId"));
        assertEquals(7, e.productos.unidades(e.gorraId, "unica"));
        api(404, () -> e.productoServicio.actualizar(e.staff, "99999999-0000-4000-8000-000000000000", datos("X", "otro", "1", null)));
        api(403, () -> e.productoServicio.actualizar(e.comprador, e.gorraId, datos("X", "otro", "1", null)));
    }

    @Test
    void eliminar_soloAdmin() {
        api(401, () -> e.productoServicio.eliminar(null, e.gorraId));
        api(403, () -> e.productoServicio.eliminar(e.staff, e.gorraId));
        e.productoServicio.eliminar(e.admin, e.gorraId);
        api(404, () -> e.productoServicio.eliminar(e.admin, e.gorraId));
        api(404, () -> e.productoServicio.obtener(e.gorraId));
    }

    @Test
    void listar_filtraYPagina() {
        assertEquals(2, e.productoServicio.listar(null, null, null, null, null).size());
        assertEquals(1, e.productoServicio.listar("gorra", null, null, null, null).size());
        assertEquals(1, e.productoServicio.listar(null, null, null, 2, 1).size());
        assertEquals(0, e.productoServicio.listar(null, null, null, 3, 1).size());
        api(400, () -> e.productoServicio.listar("nada", null, null, null, null));
        api(400, () -> e.productoServicio.listar(null, null, null, 0, null));
        api(400, () -> e.productoServicio.listar(null, null, null, null, 101));
        api(400, () -> e.productoServicio.listar(null, "no-uuid", null, null, null));
    }

    @Test
    void vista_omiteLosCamposOpcionalesNulos() {
        Map<String, Object> p = e.productoServicio.obtener(e.gorraId);
        assertFalse(p.containsKey("jugadorId"));
        assertFalse(p.containsKey("patrocinadorId"));
        assertTrue(p.containsKey("stock"));
    }
}
