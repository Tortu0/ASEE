package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Carrito;
import com.asee.compras.dominio.EstadoPedido;
import com.asee.compras.dominio.EventoCompra;
import com.asee.compras.dominio.Identidad;
import com.asee.compras.dominio.Pedido;
import com.asee.compras.dominio.Producto;
import com.asee.compras.dominio.Socio;
import com.asee.compras.dominio.StockTalla;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Dobles en memoria de los puertos. Copian en profundidad al leer y al guardar, como haría una base de datos real. */
final class Fakes {

    private Fakes() {}

    @SuppressWarnings("unchecked")
    static <T extends Serializable> T copia(T objeto) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(objeto); }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (T) in.readObject(); }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static final class RelojManual extends Clock {
        Instant ahora = Instant.parse("2026-11-12T10:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
        void avanzarSegundos(long s) { ahora = ahora.plusSeconds(s); }
    }

    static final class ProductosFake implements ProductoRepositorio {
        final Map<String, Producto> datos = new LinkedHashMap<>();
        int llamadasDescuento = 0;
        int fallarEnLlamada = -1;      // simula que otro comprador se queda con el stock justo en esa llamada

        @Override public List<Producto> listar(String categoria, String jugadorId, String patrocinadorId, int pagina, int tamano) {
            List<Producto> todos = new ArrayList<>();
            for (Producto p : datos.values()) {
                if ((categoria == null || categoria.equals(p.categoria)) && (jugadorId == null || jugadorId.equals(p.jugadorId))
                        && (patrocinadorId == null || patrocinadorId.equals(p.patrocinadorId))) todos.add(copia(p));
            }
            todos.sort(Comparator.comparing(p -> p.nombre));
            int desde = Math.min(todos.size(), (pagina - 1) * tamano);
            return todos.subList(desde, Math.min(todos.size(), desde + tamano));
        }
        @Override public Optional<Producto> porId(String id) { return Optional.ofNullable(datos.get(id)).map(Fakes::copia); }
        @Override public Producto guardar(Producto p) { datos.put(p.id, copia(p)); return copia(p); }
        @Override public boolean eliminar(String id) { return datos.remove(id) != null; }
        @Override public boolean descontarStock(String productoId, String talla, int unidades) {
            llamadasDescuento++;
            if (llamadasDescuento == fallarEnLlamada) return false;
            Producto p = datos.get(productoId);
            if (p == null) return false;
            for (StockTalla s : p.stock) {
                if (s.talla.equals(talla)) { if (s.unidades < unidades) return false; s.unidades -= unidades; return true; }
            }
            return false;
        }
        @Override public void reponerStock(String productoId, String talla, int unidades) {
            for (StockTalla s : datos.get(productoId).stock) if (s.talla.equals(talla)) s.unidades += unidades;
        }
        int unidades(String productoId, String talla) {
            return datos.get(productoId).stock.stream().filter(s -> s.talla.equals(talla)).findFirst().orElseThrow().unidades;
        }
    }

    static final class CarritosFake implements CarritoRepositorio {
        final Map<String, Carrito> datos = new LinkedHashMap<>();
        @Override public Carrito obtener(String usuarioId) { return datos.containsKey(usuarioId) ? copia(datos.get(usuarioId)) : new Carrito(usuarioId); }
        @Override public void guardar(Carrito c) { datos.put(c.id, copia(c)); }
        @Override public void vaciar(String usuarioId) { datos.remove(usuarioId); }
    }

    static final class PedidosFake implements PedidoRepositorio {
        final Map<String, Pedido> datos = new LinkedHashMap<>();
        @Override public Optional<Pedido> porId(String id) { return Optional.ofNullable(datos.get(id)).map(Fakes::copia); }
        @Override public boolean insertar(Pedido p) { if (datos.containsKey(p.id)) return false; datos.put(p.id, copia(p)); return true; }
        @Override public void guardar(Pedido p) { datos.put(p.id, copia(p)); }
        @Override public void eliminar(String id) { datos.remove(id); }
        @Override public List<Pedido> listar(String usuarioId, EstadoPedido estado) {
            List<Pedido> r = new ArrayList<>();
            for (Pedido p : datos.values()) if ((usuarioId == null || usuarioId.equals(p.usuarioId)) && (estado == null || estado == p.estado)) r.add(copia(p));
            r.sort(Comparator.comparing((Pedido p) -> p.fechaPedido).reversed());
            return r;
        }
        @Override public Optional<Pedido> cancelarSiCancelable(String id) {
            Pedido p = datos.get(id);
            if (p == null || (p.estado != EstadoPedido.PAGADO && p.estado != EstadoPedido.PREPARANDO)) return Optional.empty();
            p.estado = EstadoPedido.CANCELADO; p.reembolsoEstado = "pendiente";
            return Optional.of(copia(p));
        }
        @Override public boolean cambiarEstadoSi(String id, EstadoPedido desde, EstadoPedido hasta) {
            Pedido p = datos.get(id);
            if (p == null || p.estado != desde) return false;
            p.estado = hasta; return true;
        }
        @Override public boolean marcarStockRepuesto(String id) {
            Pedido p = datos.get(id);
            if (p == null || p.stockRepuesto) return false;
            p.stockRepuesto = true; return true;
        }
        @Override public void fijarReembolso(String id, String estado) { datos.get(id).reembolsoEstado = estado; }
        @Override public boolean marcarEventoPublicado(String id) {
            Pedido p = datos.get(id);
            if (p == null || p.eventoPublicado) return false;
            p.eventoPublicado = true; return true;
        }
        @Override public List<Pedido> pendientesDeEvento(int max) {
            List<Pedido> r = new ArrayList<>();
            for (Pedido p : datos.values()) if (p.eventoId != null && !p.eventoPublicado && r.size() < max) r.add(copia(p));
            return r;
        }
    }

    static final class UsuariosFake implements UsuariosPort {
        Socio socio = new Socio("activo", new BigDecimal("15"));
        RuntimeException error;
        int llamadas = 0;
        Identidad ultimaIdentidad;
        @Override public Socio consultarSocio(Identidad quien, String usuarioId) {
            llamadas++; ultimaIdentidad = quien;
            if (error != null) throw error;
            return socio;
        }
    }

    static final class PasarelaFake implements PasarelaPago {
        ResultadoPago alCobrar = ResultadoPago.COBRADO;
        ResultadoPago alConsultar = ResultadoPago.DESCONOCIDO;
        boolean reembolsoOk = true;
        boolean consultaLanza = false;
        int cobros = 0, consultas = 0, reembolsos = 0;
        final List<String> referencias = new ArrayList<>();
        @Override public ResultadoPago cobrar(String referencia, BigDecimal importe, String metodo) { cobros++; referencias.add(referencia); return alCobrar; }
        @Override public ResultadoPago consultar(String referencia) { consultas++; if (consultaLanza) throw new RuntimeException("sin respuesta"); return alConsultar; }
        @Override public boolean reembolsar(String referencia, BigDecimal importe) { reembolsos++; return reembolsoOk; }
    }

    static final class PublicadorFake implements PublicadorEventos {
        final List<EventoCompra> publicados = new ArrayList<>();
        final List<String> claves = new ArrayList<>();
        boolean fallar = false;
        @Override public void publicar(String routingKey, EventoCompra evento) {
            if (fallar) throw new RuntimeException("broker caído");
            claves.add(routingKey); publicados.add(evento);
        }
    }
}
