package com.asee.compras.config;

import com.asee.compras.aplicacion.ProductoRepositorio;
import com.asee.compras.dominio.Producto;
import com.asee.compras.dominio.StockTalla;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "compras.datos-ejemplo", havingValue = "true")
public class DatosEjemploInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosEjemploInitializer.class);

    private final ProductoRepositorio productos;

    @Value("${SUPABASE_URL:}")
    private String supabaseUrl;

    public DatosEjemploInitializer(ProductoRepositorio productos) {
        this.productos = productos;
    }

    @Override
    public void run(String... args) {
        try {
            if (!productos.listar(null, null, null, 1, 1).isEmpty()) {
                log.info("Compras: la colección de productos ya tiene datos, no se carga nada");
                return;
            }

            List<Producto> iniciales = List.of(
                    crearProducto(
                            productoId(1),
                            "Camiseta 1ª Equipación 2026-2027",
                            "Camiseta oficial azul marino del CB Madroñera con tejido transpirable de competición.",
                            "camiseta",
                            new BigDecimal("39.95"),
                            List.of(
                                    new StockTalla("S", 12),
                                    new StockTalla("M", 20),
                                    new StockTalla("L", 18),
                                    new StockTalla("XL", 10)
                            ),
                            fotoProducto("primera_equipacion.png")
                    ),
                    crearProducto(
                            productoId(2),
                            "Camiseta 2ª Equipación Blanca",
                            "Camiseta visitante blanca con detalles en azul y dorsal personalizable en tienda.",
                            "camiseta",
                            new BigDecimal("37.95"),
                            List.of(
                                    new StockTalla("S", 8),
                                    new StockTalla("M", 15),
                                    new StockTalla("L", 14),
                                    new StockTalla("XL", 6)
                            ),
                            fotoProducto("segunda_equipacion.png")
                    ),
                    crearProducto(
                            productoId(3),
                            "Bufanda Oficial CB Madroñera",
                            "Bufanda de punto jacquard con el escudo del club y lema de la afición.",
                            "bufanda",
                            new BigDecimal("14.50"),
                            List.of(new StockTalla("unica", 35)),
                            fotoProducto("bufanda.png")
                    ),
                    crearProducto(
                            productoId(4),
                            "Gorra Oficial Escudo Bordado",
                            "Gorra deportiva ajustable con visera curva y escudo del CB Madroñera bordado en relieve.",
                            "gorra",
                            new BigDecimal("16.00"),
                            List.of(new StockTalla("unica", 25)),
                            fotoProducto("gorra.png")
                    ),
                    crearProducto(
                            productoId(5),
                            "Mochila Deportiva de Entrenamiento",
                            "Mochila resistente con compartimento inferior ventilado para zapatillas y balón.",
                            "accesorio",
                            new BigDecimal("29.90"),
                            List.of(new StockTalla("unica", 15)),
                            fotoProducto("mochila.png")
                    ),
                    crearProducto(
                            productoId(6),
                            "Balón Oficial Edición Club",
                            "Balón de baloncesto talla 7 en piel sintética de agarre superior con logotipo del club.",
                            "otro",
                            new BigDecimal("24.95"),
                            List.of(new StockTalla("unica", 20)),
                            fotoProducto("balon.png")
                    )
            );

            for (Producto p : iniciales) {
                productos.guardar(p);
            }
            log.info("Compras: cargados {} productos de ejemplo en el catálogo", iniciales.size());
        } catch (Exception e) {
            // Si MongoDB no está disponible en el arranque, el servicio sigue iniciándose
            log.warn("No se han cargado los productos de ejemplo: {}", e.getMessage());
        }
    }

    private static Producto crearProducto(String id, String nombre, String descripcion,
                                          String categoria, BigDecimal precio, List<StockTalla> stock, List<String> fotos) {
        Producto p = new Producto();
        p.id = id;
        p.nombre = nombre;
        p.descripcion = descripcion;
        p.categoria = categoria;
        p.precio = precio;
        p.stock = stock;
        p.fotos = fotos;
        return p;
    }

    private List<String> fotoProducto(String archivo) {
        if (supabaseUrl == null || supabaseUrl.isBlank()) return List.of();
        return List.of(supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/public/club-media/productos/" + archivo);
    }

    private static String productoId(int n) {
        return String.format("40000000-0000-0000-0000-%012d", n);
    }
}
