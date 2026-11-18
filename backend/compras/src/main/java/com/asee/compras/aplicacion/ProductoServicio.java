package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Identidad;
import com.asee.compras.dominio.Producto;
import com.asee.compras.dominio.StockTalla;
import com.asee.compras.dominio.Calculadora;
import com.asee.compras.dominio.Vistas;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProductoServicio {

    public static final Set<String> CATEGORIAS = Set.of("camiseta", "bufanda", "gorra", "accesorio", "otro");

    /** Cuerpo de POST/PUT /productos (esquema ProductoCreate). */
    public record DatosProducto(String nombre, String descripcion, String categoria, BigDecimal precio,
                                List<StockTalla> stock, List<String> fotos, String jugadorId, String patrocinadorId) {}

    private final ProductoRepositorio productos;
    private final java.util.function.Supplier<String> ids;

    public ProductoServicio(ProductoRepositorio productos, java.util.function.Supplier<String> ids) {
        this.productos = productos;
        this.ids = ids;
    }

    public List<Map<String, Object>> listar(String categoria, String jugadorId, String patrocinadorId, Integer pagina, Integer tamano) {
        if (categoria != null && !CATEGORIAS.contains(categoria)) throw ApiException.invalida("categoria no válida");
        if (jugadorId != null) Validaciones.uuid("jugadorId", jugadorId);
        if (patrocinadorId != null) Validaciones.uuid("patrocinadorId", patrocinadorId);
        int p = pagina == null ? 1 : pagina, t = tamano == null ? 20 : tamano;
        if (p < 1) throw ApiException.invalida("pagina debe ser 1 o mayor");
        if (t < 1 || t > 100) throw ApiException.invalida("tamano debe estar entre 1 y 100");
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Producto x : productos.listar(categoria, jugadorId, patrocinadorId, p, t)) salida.add(Vistas.producto(x));
        return salida;
    }

    public Map<String, Object> obtener(String productoId) {
        return Vistas.producto(productos.porId(productoId).orElseThrow(() -> ApiException.noEncontrado("Producto no encontrado")));
    }

    public Map<String, Object> crear(Identidad quien, DatosProducto datos) {
        exigirStaff(quien);
        Producto p = construir(datos);
        p.id = ids.get();
        return Vistas.producto(productos.guardar(p));
    }

    public Map<String, Object> actualizar(Identidad quien, String productoId, DatosProducto datos) {
        exigirStaff(quien);
        productos.porId(productoId).orElseThrow(() -> ApiException.noEncontrado("Producto no encontrado"));
        Producto p = construir(datos);
        p.id = productoId;
        return Vistas.producto(productos.guardar(p));
    }

    public void eliminar(Identidad quien, String productoId) {
        if (quien == null) throw ApiException.noAutenticado("No autenticado");
        if (!quien.esAdmin()) throw ApiException.prohibido("Solo un administrador puede retirar productos");
        if (!productos.eliminar(productoId)) throw ApiException.noEncontrado("Producto no encontrado");
    }

    private static void exigirStaff(Identidad quien) {
        if (quien == null) throw ApiException.noAutenticado("No autenticado");
        if (!quien.esStaff()) throw ApiException.prohibido("Solo staff o admin pueden gestionar el catálogo");
    }

    private static Producto construir(DatosProducto d) {
        if (d == null) throw ApiException.invalida("El cuerpo de la petición es obligatorio");
        Validaciones.texto("nombre", d.nombre());
        if (d.categoria() == null || !CATEGORIAS.contains(d.categoria())) {
            throw ApiException.invalida("categoria no válida", "Valores permitidos: " + String.join(", ", new java.util.TreeSet<>(CATEGORIAS)));
        }
        if (d.precio() == null || d.precio().signum() < 0) throw ApiException.invalida("precio es obligatorio y no puede ser negativo");
        if (d.jugadorId() != null) Validaciones.uuid("jugadorId", d.jugadorId());
        if (d.patrocinadorId() != null) Validaciones.uuid("patrocinadorId", d.patrocinadorId());

        Producto p = new Producto();
        p.nombre = d.nombre().trim();
        p.descripcion = d.descripcion();
        p.categoria = d.categoria();
        p.precio = Calculadora.dinero(d.precio());
        Set<String> vistas = new HashSet<>();
        if (d.stock() != null) {
            for (StockTalla s : d.stock()) {
                if (s == null || s.talla == null || s.talla.isBlank()) throw ApiException.invalida("Cada elemento de stock necesita una talla");
                if (s.unidades < 0) throw ApiException.invalida("Las unidades de stock no pueden ser negativas");
                if (!vistas.add(s.talla)) throw ApiException.invalida("Talla repetida en el stock: " + s.talla);
                p.stock.add(new StockTalla(s.talla, s.unidades));
            }
        }
        if (d.fotos() != null) p.fotos.addAll(d.fotos());
        p.jugadorId = d.jugadorId();
        p.patrocinadorId = d.patrocinadorId();
        return p;
    }

    /** UUID aleatorio como identificador de productos, carritos y pedidos. */
    public static String nuevoId() {
        return UUID.randomUUID().toString();
    }
}
