package com.asee.compras.dominio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Representaciones JSON del contrato (productoId, items, fechaPedido...). Los campos nulos se omiten. */
public final class Vistas {

    private Vistas() {}

    private static void poner(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }

    public static Map<String, Object> producto(Producto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productoId", p.id);
        m.put("nombre", p.nombre);
        poner(m, "descripcion", p.descripcion);
        m.put("categoria", p.categoria);
        m.put("precio", p.precio);
        List<Map<String, Object>> stock = new ArrayList<>();
        for (StockTalla s : p.stock) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("talla", s.talla);
            t.put("unidades", s.unidades);
            stock.add(t);
        }
        m.put("stock", stock);
        m.put("fotos", p.fotos == null ? List.of() : p.fotos);
        poner(m, "jugadorId", p.jugadorId);
        poner(m, "patrocinadorId", p.patrocinadorId);
        return m;
    }

    public static Map<String, Object> item(ItemCarrito i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", i.itemId);
        m.put("productoId", i.productoId);
        poner(m, "nombre", i.nombre);
        poner(m, "talla", i.talla);
        m.put("cantidad", i.cantidad);
        m.put("precioUnitario", i.precioUnitario);
        return m;
    }

    public static Map<String, Object> carrito(String usuarioId, List<ItemCarrito> items, Calculadora.Totales t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usuarioId", usuarioId);
        List<Map<String, Object>> lista = new ArrayList<>();
        for (ItemCarrito i : items) lista.add(item(i));
        m.put("items", lista);
        m.put("subtotal", t.subtotal());
        m.put("descuentoSocio", t.descuentoSocio());
        m.put("total", t.total());
        return m;
    }

    public static Map<String, Object> pedido(Pedido p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pedidoId", p.id);
        m.put("usuarioId", p.usuarioId);
        List<Map<String, Object>> lista = new ArrayList<>();
        for (ItemCarrito i : p.items) lista.add(item(i));
        m.put("items", lista);
        m.put("total", p.total);
        m.put("descuentoSocio", p.descuentoSocio);
        m.put("direccionEnvio", p.direccionEnvio);
        m.put("metodoPago", p.metodoPago);
        m.put("estado", p.estado.valor());
        m.put("fechaPedido", p.fechaPedido == null ? null : p.fechaPedido.toString());
        return m;
    }
}
