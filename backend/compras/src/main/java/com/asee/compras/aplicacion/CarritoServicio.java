package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Calculadora;
import com.asee.compras.dominio.Carrito;
import com.asee.compras.dominio.Identidad;
import com.asee.compras.dominio.ItemCarrito;
import com.asee.compras.dominio.Producto;
import com.asee.compras.dominio.Socio;
import com.asee.compras.dominio.StockTalla;
import com.asee.compras.dominio.Vistas;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Carrito persistente del usuario autenticado. Modificarlo NO reserva stock (el stock se descuenta al pagar). */
public class CarritoServicio {

    /** Cuerpo de POST /carrito/items (esquema ItemCarritoCreate). */
    public record DatosItem(String productoId, String talla, Integer cantidad) {}

    private final ProductoRepositorio productos;
    private final CarritoRepositorio carritos;
    private final UsuariosPort usuarios;
    private final Supplier<String> ids;

    public CarritoServicio(ProductoRepositorio productos, CarritoRepositorio carritos, UsuariosPort usuarios, Supplier<String> ids) {
        this.productos = productos;
        this.carritos = carritos;
        this.usuarios = usuarios;
        this.ids = ids;
    }

    /** 404 si el usuario no existe en Usuarios; 503 si Usuarios no está disponible. */
    public Map<String, Object> ver(Identidad quien) {
        exigir(quien);
        Socio socio = usuarios.consultarSocio(quien, quien.usuarioId());
        return vista(quien.usuarioId(), carritos.obtener(quien.usuarioId()).items, socio);
    }

    public void vaciar(Identidad quien) {
        exigir(quien);
        carritos.vaciar(quien.usuarioId());
    }

    public Map<String, Object> anadir(Identidad quien, DatosItem datos) {
        exigir(quien);
        if (datos == null) throw ApiException.invalida("El cuerpo de la petición es obligatorio");
        Validaciones.uuid("productoId", datos.productoId());
        Validaciones.texto("talla", datos.talla());
        int cantidad = cantidadValida(datos.cantidad());

        Producto p = productos.porId(datos.productoId()).orElseThrow(() -> ApiException.noEncontrado("El producto no existe"));
        StockTalla stock = talla(p, datos.talla());
        if (stock == null) throw ApiException.invalida("La talla '" + datos.talla() + "' no existe en este producto");

        Carrito c = carritos.obtener(quien.usuarioId());
        ItemCarrito existente = c.items.stream()
                .filter(i -> i.productoId.equals(p.id) && i.talla.equals(datos.talla())).findFirst().orElse(null);
        int total = (existente == null ? 0 : existente.cantidad) + cantidad;
        if (total > stock.unidades) throw ApiException.conflicto("No hay stock suficiente de la talla " + datos.talla());

        Socio socio = usuarios.consultarSocio(quien, quien.usuarioId());   // antes de modificar nada: si falla, no se guarda

        if (existente != null) {
            existente.cantidad = total;
        } else {
            ItemCarrito nuevo = new ItemCarrito();
            nuevo.itemId = ids.get();
            nuevo.productoId = p.id;
            nuevo.nombre = p.nombre;
            nuevo.talla = datos.talla();
            nuevo.cantidad = cantidad;
            nuevo.precioUnitario = p.precio;
            c.items.add(nuevo);
        }
        carritos.guardar(c);
        return vista(quien.usuarioId(), c.items, socio);
    }

    public Map<String, Object> cambiarCantidad(Identidad quien, String itemId, Integer cantidadNueva) {
        exigir(quien);
        int cantidad = cantidadValida(cantidadNueva);
        Carrito c = carritos.obtener(quien.usuarioId());
        ItemCarrito item = c.items.stream().filter(i -> i.itemId.equals(itemId)).findFirst()
                .orElseThrow(() -> ApiException.noEncontrado("El elemento no está en tu carrito"));
        Producto p = productos.porId(item.productoId).orElseThrow(() -> ApiException.conflicto("El producto ya no está disponible"));
        StockTalla stock = talla(p, item.talla);
        if (stock == null || cantidad > stock.unidades) throw ApiException.conflicto("No hay stock suficiente de la talla " + item.talla);

        Socio socio = usuarios.consultarSocio(quien, quien.usuarioId());
        item.cantidad = cantidad;
        carritos.guardar(c);
        return vista(quien.usuarioId(), c.items, socio);
    }

    public void quitar(Identidad quien, String itemId) {
        exigir(quien);
        Carrito c = carritos.obtener(quien.usuarioId());
        if (!c.items.removeIf(i -> i.itemId.equals(itemId))) throw ApiException.noEncontrado("El elemento no está en tu carrito");
        carritos.guardar(c);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Se muestran el nombre y el precio ACTUALES del catálogo; al pagar se vuelven a revalidar. */
    private Map<String, Object> vista(String usuarioId, List<ItemCarrito> guardados, Socio socio) {
        List<ItemCarrito> actuales = new ArrayList<>();
        for (ItemCarrito i : guardados) {
            ItemCarrito copia = i.copia();
            productos.porId(i.productoId).ifPresent(p -> {
                copia.nombre = p.nombre;
                copia.precioUnitario = p.precio;
            });
            actuales.add(copia);
        }
        return Vistas.carrito(usuarioId, actuales, Calculadora.totales(actuales, socio.descuentoAplicable()));
    }

    static StockTalla talla(Producto p, String talla) {
        return p.stock.stream().filter(s -> s.talla.equals(talla)).findFirst().orElse(null);
    }

    private static int cantidadValida(Integer cantidad) {
        if (cantidad == null || cantidad < 1) throw ApiException.invalida("La cantidad debe ser un entero positivo");
        return cantidad;
    }

    private static void exigir(Identidad quien) {
        if (quien == null) throw ApiException.noAutenticado("No autenticado");
    }
}
