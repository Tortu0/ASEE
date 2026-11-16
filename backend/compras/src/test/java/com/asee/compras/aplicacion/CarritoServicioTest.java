package com.asee.compras.aplicacion;

import com.asee.compras.aplicacion.CarritoServicio.DatosItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.asee.compras.aplicacion.PedidoServicioTest.api;
import static org.junit.jupiter.api.Assertions.*;

class CarritoServicioTest {

    Escenario e;

    @BeforeEach
    void preparar() { e = new Escenario(); }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> items(Map<String, Object> carrito) { return (List<Map<String, Object>>) carrito.get("items"); }

    @Test
    void carritoNuevo_estaVacio_conTotalesACero() {
        Map<String, Object> c = e.carritoServicio.ver(e.comprador);
        assertTrue(items(c).isEmpty());
        assertEquals(new BigDecimal("0.00"), c.get("total"));
        assertEquals(Escenario.COMPRADOR_ID, c.get("usuarioId"));
    }

    @Test
    void anadir_creaLaLinea_ycalculaTotalesConDescuentoDeSocio() {
        Map<String, Object> c = e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "M", 2));
        assertEquals(1, items(c).size());
        assertEquals("Camiseta oficial", items(c).get(0).get("nombre"));
        assertEquals(new BigDecimal("99.80"), c.get("subtotal"));
        assertEquals(new BigDecimal("14.97"), c.get("descuentoSocio"));
        assertEquals(new BigDecimal("84.83"), c.get("total"));
        assertEquals(5, e.productos.unidades(e.camisetaId, "M"), "el carrito NO reserva stock");
    }

    @Test
    void anadir_elMismoProductoYTalla_sumaLaCantidad_yOtraTallaEsOtraLinea() {
        e.alCarrito(e.comprador, e.camisetaId, "M", 2);
        e.alCarrito(e.comprador, e.camisetaId, "M", 1);
        Map<String, Object> c = e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "L", 1));
        assertEquals(2, items(c).size());
        assertEquals(3, items(c).get(0).get("cantidad"));
    }

    @Test
    void anadir_validaciones() {
        api(401, () -> e.carritoServicio.anadir(null, new DatosItem(e.camisetaId, "M", 1)));
        api(400, () -> e.carritoServicio.anadir(e.comprador, null));
        api(400, () -> e.carritoServicio.anadir(e.comprador, new DatosItem("no-uuid", "M", 1)));
        api(400, () -> e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "", 1)));
        api(400, () -> e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "M", 0)));
        api(400, () -> e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "M", null)));
        api(400, () -> e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "XXL", 1)));   // talla inexistente
        api(404, () -> e.carritoServicio.anadir(e.comprador, new DatosItem("99999999-0000-4000-8000-000000000000", "M", 1)));
    }

    @Test
    void anadir_masDelStockDisponible_409_tambienAcumulando() {
        api(409, () -> e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "L", 3)));
        e.alCarrito(e.comprador, e.camisetaId, "L", 2);
        api(409, () -> e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "L", 1)));
    }

    @Test
    void anadir_siUsuariosFalla_noSeGuardaNada() {
        e.usuarios.error = ApiException.noDisponible("caído");
        api(503, () -> e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "M", 1)));
        e.usuarios.error = null;
        assertTrue(items(e.carritoServicio.ver(e.comprador)).isEmpty());
        e.usuarios.error = ApiException.noEncontrado("no existe");
        api(404, () -> e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "M", 1)));
    }

    @Test
    void ver_muestraElPrecioActualDelCatalogo() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.productos.datos.get(e.gorraId).precio = new BigDecimal("30.00");
        e.productos.datos.get(e.gorraId).nombre = "Gorra nueva";
        Map<String, Object> c = e.carritoServicio.ver(e.comprador);
        assertEquals(new BigDecimal("30.00"), items(c).get(0).get("precioUnitario"));
        assertEquals("Gorra nueva", items(c).get(0).get("nombre"));
    }

    @Test
    void ver_siUsuariosNoResponde_503() {
        e.usuarios.error = ApiException.noDisponible("caído");
        api(503, () -> e.carritoServicio.ver(e.comprador));
    }

    @Test
    void cadaUsuarioTieneSuPropioCarrito() {
        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        assertTrue(items(e.carritoServicio.ver(e.otro)).isEmpty());
    }

    @Test
    void cambiarCantidad_quitar_y_vaciar() {
        Map<String, Object> c = e.carritoServicio.anadir(e.comprador, new DatosItem(e.camisetaId, "M", 1));
        String itemId = (String) items(c).get(0).get("itemId");

        assertEquals(4, items(e.carritoServicio.cambiarCantidad(e.comprador, itemId, 4)).get(0).get("cantidad"));
        api(409, () -> e.carritoServicio.cambiarCantidad(e.comprador, itemId, 6));
        api(400, () -> e.carritoServicio.cambiarCantidad(e.comprador, itemId, 0));
        api(404, () -> e.carritoServicio.cambiarCantidad(e.comprador, "no-existe", 1));
        api(404, () -> e.carritoServicio.cambiarCantidad(e.otro, itemId, 1));   // el elemento es de otro usuario

        api(404, () -> e.carritoServicio.quitar(e.otro, itemId));
        e.carritoServicio.quitar(e.comprador, itemId);
        assertTrue(items(e.carritoServicio.ver(e.comprador)).isEmpty());
        api(404, () -> e.carritoServicio.quitar(e.comprador, itemId));

        e.alCarrito(e.comprador, e.gorraId, "unica", 1);
        e.carritoServicio.vaciar(e.comprador);
        assertTrue(items(e.carritoServicio.ver(e.comprador)).isEmpty());
        api(401, () -> e.carritoServicio.vaciar(null));
    }
}
