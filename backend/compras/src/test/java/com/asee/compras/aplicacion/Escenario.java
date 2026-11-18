package com.asee.compras.aplicacion;

import com.asee.compras.aplicacion.CarritoServicio.DatosItem;
import com.asee.compras.dominio.Identidad;
import com.asee.compras.dominio.Producto;
import com.asee.compras.dominio.StockTalla;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Montaje común de los tests: servicios reales sobre dobles en memoria, con productos y usuarios de ejemplo. */
class Escenario {
    static final String COMPRADOR_ID = "11111111-1111-4111-8111-111111111111";
    static final String OTRO_ID = "22222222-2222-4222-8222-222222222222";
    static final String STAFF_ID = "33333333-3333-4333-8333-333333333333";
    static final String ADMIN_ID = "44444444-4444-4444-8444-444444444444";

    final Identidad comprador = new Identidad(COMPRADOR_ID, "socio", "tok-comprador");
    final Identidad otro = new Identidad(OTRO_ID, "visitante", null);
    final Identidad staff = new Identidad(STAFF_ID, "staff", null);
    final Identidad admin = new Identidad(ADMIN_ID, "admin", null);

    final Fakes.RelojManual reloj = new Fakes.RelojManual();
    final Fakes.ProductosFake productos = new Fakes.ProductosFake();
    final Fakes.CarritosFake carritos = new Fakes.CarritosFake();
    final Fakes.PedidosFake pedidos = new Fakes.PedidosFake();
    final Fakes.UsuariosFake usuarios = new Fakes.UsuariosFake();
    final Fakes.PasarelaFake pasarela = new Fakes.PasarelaFake();
    final Fakes.PublicadorFake publicador = new Fakes.PublicadorFake();
    private final AtomicInteger contador = new AtomicInteger();

    final ProductoServicio productoServicio;
    final CarritoServicio carritoServicio;
    final PedidoServicio pedidoServicio;

    String camisetaId, gorraId;

    Escenario() {
        java.util.function.Supplier<String> ids = () -> String.format("aaaaaaaa-0000-4000-8000-%012d", contador.incrementAndGet());
        productoServicio = new ProductoServicio(productos, ids);
        carritoServicio = new CarritoServicio(productos, carritos, usuarios, ids);
        pedidoServicio = new PedidoServicio(productos, carritos, pedidos, usuarios, pasarela, publicador, reloj, Duration.ofSeconds(60));
        camisetaId = producto("Camiseta oficial", "camiseta", "49.90", new StockTalla("M", 5), new StockTalla("L", 2));
        gorraId = producto("Gorra", "gorra", "20.00", new StockTalla("unica", 3));
    }

    String producto(String nombre, String categoria, String precio, StockTalla... stock) {
        Producto p = new Producto();
        p.id = String.format("bbbbbbbb-0000-4000-8000-%012d", productos.datos.size() + 1);
        p.nombre = nombre;
        p.categoria = categoria;
        p.precio = new BigDecimal(precio);
        p.stock = new java.util.ArrayList<>(List.of(stock));
        productos.datos.put(p.id, p);
        return p.id;
    }

    void alCarrito(Identidad quien, String productoId, String talla, int cantidad) {
        carritoServicio.anadir(quien, new DatosItem(productoId, talla, cantidad));
    }

    PedidoServicio.DatosPedido datos() {
        return new PedidoServicio.DatosPedido("Calle Mayor 12, Madroñera", "tarjeta");
    }
}
