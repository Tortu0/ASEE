package com.asee.compras.aplicacion;

import com.asee.compras.dominio.Calculadora;
import com.asee.compras.dominio.Carrito;
import com.asee.compras.dominio.EstadoPedido;
import com.asee.compras.dominio.EventoCompra;
import com.asee.compras.dominio.FlujoEstados;
import com.asee.compras.dominio.Identidad;
import com.asee.compras.dominio.ItemCarrito;
import com.asee.compras.dominio.Pedido;
import com.asee.compras.dominio.Producto;
import com.asee.compras.dominio.Socio;
import com.asee.compras.dominio.StockTalla;
import com.asee.compras.dominio.Vistas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pedidos (memoria 3.3, 3.4 y 3.6):
 *  - POST /pedidos es idempotente: la Idempotency-Key fija el id del pedido y la referencia de cobro.
 *  - Revalida carrito, stock y descuento (consulta a Usuarios; sin Usuarios no se confirma un descuento -> 503).
 *  - El cobro externo no es transaccional con la base de datos: el pedido queda "pendiente" mientras se cobra y,
 *    si el resultado es incierto, se consulta a la pasarela antes de repetir el cobro.
 *  - Tras el pago se guarda el evento "pedido creado" junto al pedido y un publicador reintenta el envío.
 */
public class PedidoServicio {

    private static final System.Logger LOG = System.getLogger("compras.pedidos");

    public static final Set<String> METODOS_PAGO = Set.of("tarjeta", "bizum", "paypal");
    public static final String ROUTING_KEY_PEDIDO = "pedido.creado";
    private static final int MAX_CLAVE = 100;

    /** Cuerpo de POST /pedidos (esquema PedidoCreate). */
    public record DatosPedido(String direccionEnvio, String metodoPago) {}

    private final ProductoRepositorio productos;
    private final CarritoRepositorio carritos;
    private final PedidoRepositorio pedidos;
    private final UsuariosPort usuarios;
    private final PasarelaPago pasarela;
    private final PublicadorEventos publicador;
    private final Clock reloj;
    private final Duration pagoEnCursoMaximo;

    public PedidoServicio(ProductoRepositorio productos, CarritoRepositorio carritos, PedidoRepositorio pedidos,
                          UsuariosPort usuarios, PasarelaPago pasarela, PublicadorEventos publicador,
                          Clock reloj, Duration pagoEnCursoMaximo) {
        this.productos = productos;
        this.carritos = carritos;
        this.pedidos = pedidos;
        this.usuarios = usuarios;
        this.pasarela = pasarela;
        this.publicador = publicador;
        this.reloj = reloj;
        this.pagoEnCursoMaximo = pagoEnCursoMaximo;
    }

    // ═════════════ POST /pedidos ═════════════

    public Map<String, Object> crear(Identidad quien, String claveIdempotencia, DatosPedido datos) {
        exigir(quien);
        if (claveIdempotencia == null || claveIdempotencia.isBlank() || claveIdempotencia.length() > MAX_CLAVE) {
            throw ApiException.invalida("La cabecera Idempotency-Key es obligatoria (entre 1 y " + MAX_CLAVE + " caracteres)");
        }
        if (datos == null) throw ApiException.invalida("El cuerpo de la petición es obligatorio");
        Validaciones.texto("direccionEnvio", datos.direccionEnvio());
        if (datos.metodoPago() == null || !METODOS_PAGO.contains(datos.metodoPago())) {
            throw ApiException.invalida("metodoPago no válido", "Valores permitidos: bizum, paypal, tarjeta");
        }
        String direccion = datos.direccionEnvio().trim();
        String huella = huella(direccion, datos.metodoPago());
        String pedidoId = idEstable(quien.usuarioId(), claveIdempotencia);

        Pedido previo = pedidos.porId(pedidoId).orElse(null);
        if (previo != null) return retomar(quien, previo, huella);

        Carrito carrito = carritos.obtener(quien.usuarioId());
        if (carrito.items.isEmpty()) throw ApiException.conflicto("El carrito está vacío");

        // Consulta síncrona a Usuarios ANTES de tocar el stock: 404 si el usuario no existe, 503 si no responde
        Socio socio = usuarios.consultarSocio(quien, quien.usuarioId());

        // Revalidar existencia, talla y stock, y congelar nombre y precio actuales
        List<ItemCarrito> congelados = new ArrayList<>();
        for (ItemCarrito i : carrito.items) {
            Producto p = productos.porId(i.productoId)
                    .orElseThrow(() -> ApiException.conflicto("El producto «" + i.nombre + "» ya no está disponible"));
            StockTalla s = CarritoServicio.talla(p, i.talla);
            if (s == null) throw ApiException.conflicto("La talla " + i.talla + " de «" + p.nombre + "» ya no existe");
            if (s.unidades < i.cantidad) {
                throw ApiException.conflicto("No hay stock suficiente de «" + p.nombre + "» (talla " + i.talla + ")");
            }
            ItemCarrito c = i.copia();
            c.nombre = p.nombre;
            c.precioUnitario = p.precio;
            congelados.add(c);
        }
        Calculadora.Totales t = Calculadora.totales(congelados, socio.descuentoAplicable());

        Pedido pedido = new Pedido();
        pedido.id = pedidoId;
        pedido.usuarioId = quien.usuarioId();
        pedido.huella = huella;
        pedido.items = congelados;
        pedido.subtotal = t.subtotal();
        pedido.descuentoSocio = t.descuentoSocio();
        pedido.total = t.total();
        pedido.direccionEnvio = direccion;
        pedido.metodoPago = datos.metodoPago();
        pedido.estado = EstadoPedido.PENDIENTE;
        pedido.fechaPedido = reloj.instant();
        pedido.referenciaCobro = "COBRO-" + pedidoId;
        pedido.pagoEstado = "en_curso";
        if (!pedidos.insertar(pedido)) {   // otra petición con la misma clave ganó la carrera
            throw ApiException.conflicto("Hay otra petición en curso con la misma Idempotency-Key");
        }

        // Descuento atómico de stock, con compensación si alguna línea falla
        List<ItemCarrito> descontados = new ArrayList<>();
        for (ItemCarrito i : congelados) {
            if (!productos.descontarStock(i.productoId, i.talla, i.cantidad)) {
                reponer(descontados);
                pedidos.eliminar(pedido.id);
                throw ApiException.conflicto("No hay stock suficiente de «" + i.nombre + "» (talla " + i.talla + ")");
            }
            descontados.add(i);
        }
        pedido.stockDescontado = true;
        pedidos.guardar(pedido);

        return cobrar(quien, pedido);
    }

    /** Repetición de una Idempotency-Key ya usada. */
    private Map<String, Object> retomar(Identidad quien, Pedido previo, String huella) {
        if (!huella.equals(previo.huella)) throw ApiException.conflicto("La Idempotency-Key ya se usó con otros datos");
        switch (previo.estado) {
            case CANCELADO:
                throw ApiException.conflicto("El pedido de esta Idempotency-Key fue cancelado; usa una clave nueva");
            case PENDIENTE: {
                boolean enCurso = "en_curso".equals(previo.pagoEstado)
                        && Duration.between(previo.fechaPedido, reloj.instant()).compareTo(pagoEnCursoMaximo) < 0;
                if (enCurso) throw ApiException.conflicto("Hay un pago en curso para esta Idempotency-Key");
                if (!previo.stockDescontado) {
                    throw ApiException.noDisponible("El pedido quedó a medias y requiere conciliación manual");
                }
                // Un pago incierto se consulta ANTES de repetir el cobro
                ResultadoPago r;
                try {
                    r = pasarela.consultar(previo.referenciaCobro);
                } catch (RuntimeException e) {
                    r = ResultadoPago.INCIERTO;
                }
                if (r == ResultadoPago.DESCONOCIDO) return cobrar(quien, previo);   // la pasarela nunca llegó a cobrar
                return resolverPago(quien, previo, r);
            }
            default:
                return Vistas.pedido(previo);   // ya pagado: se recupera sin cobrar ni descontar stock otra vez
        }
    }

    private Map<String, Object> cobrar(Identidad quien, Pedido pedido) {
        ResultadoPago r;
        try {
            r = pasarela.cobrar(pedido.referenciaCobro, pedido.total, pedido.metodoPago);
        } catch (RuntimeException e) {
            r = ResultadoPago.INCIERTO;
        }
        return resolverPago(quien, pedido, r);
    }

    private Map<String, Object> resolverPago(Identidad quien, Pedido pedido, ResultadoPago resultado) {
        if (resultado == ResultadoPago.COBRADO) {
            pedido.estado = EstadoPedido.PAGADO;
            pedido.pagoEstado = "completado";
            pedido.eventoId = idEvento(pedido.id);
            pedido.eventoPublicado = false;
            pedidos.guardar(pedido);
            carritos.vaciar(quien.usuarioId());
            publicarYMarcar(pedido);
            return Vistas.pedido(pedido);
        }
        if (resultado == ResultadoPago.RECHAZADO) {
            if (pedido.stockDescontado) reponer(pedido.items);
            pedidos.eliminar(pedido.id);
            throw ApiException.pagoRechazado("El pago ha sido rechazado");
        }
        pedido.pagoEstado = "incierto";
        pedidos.guardar(pedido);
        throw ApiException.noDisponible("El resultado del pago está pendiente de conciliación; "
                + "reintenta con la misma Idempotency-Key");
    }

    // ═════════════ Consultas ═════════════

    public List<Map<String, Object>> listar(Identidad quien, String usuarioIdFiltro, String estadoTexto) {
        exigir(quien);
        EstadoPedido estado = null;
        if (estadoTexto != null) {
            estado = EstadoPedido.deValor(estadoTexto);
            if (estado == null) throw ApiException.invalida("estado no válido");
        }
        if (usuarioIdFiltro != null) Validaciones.uuid("usuarioId", usuarioIdFiltro);
        String usuario;
        if (quien.esStaff()) {
            usuario = usuarioIdFiltro;   // null = todos
        } else {
            if (usuarioIdFiltro != null && !usuarioIdFiltro.equals(quien.usuarioId())) {
                throw ApiException.prohibido("Solo puedes consultar tus propios pedidos");
            }
            usuario = quien.usuarioId();
        }
        List<Map<String, Object>> salida = new ArrayList<>();
        for (Pedido p : pedidos.listar(usuario, estado)) salida.add(Vistas.pedido(p));
        return salida;
    }

    public Map<String, Object> obtener(Identidad quien, String pedidoId) {
        exigir(quien);
        Pedido p = pedidos.porId(pedidoId).orElseThrow(() -> ApiException.noEncontrado("Pedido no encontrado"));
        propietarioOStaff(quien, p);
        return Vistas.pedido(p);
    }

    // ═════════════ DELETE /pedidos/{id} ═════════════

    /** Cancela antes del envío. Repone el stock UNA sola vez y solicita el reembolso. Repetirla devuelve éxito. */
    public void cancelar(Identidad quien, String pedidoId) {
        exigir(quien);
        Pedido p = pedidos.porId(pedidoId).orElseThrow(() -> ApiException.noEncontrado("Pedido no encontrado"));
        propietarioOStaff(quien, p);
        if (p.estado == EstadoPedido.CANCELADO) {
            completarCancelacion(p);   // idempotente: termina lo que quedara a medias
            return;
        }
        if (p.estado == EstadoPedido.ENVIADO || p.estado == EstadoPedido.ENTREGADO) {
            throw ApiException.conflicto("El pedido ya fue enviado o entregado");
        }
        if (p.estado == EstadoPedido.PENDIENTE) throw ApiException.conflicto("El pedido tiene un pago en curso");

        Pedido cancelado = pedidos.cancelarSiCancelable(pedidoId).orElse(null);
        if (cancelado == null) {   // otro proceso lo cambió entre medias
            Pedido ahora = pedidos.porId(pedidoId).orElseThrow(() -> ApiException.noEncontrado("Pedido no encontrado"));
            if (ahora.estado != EstadoPedido.CANCELADO) throw ApiException.conflicto("El pedido cambió de estado y ya no se puede cancelar");
            completarCancelacion(ahora);
            return;
        }
        completarCancelacion(cancelado);
    }

    private void completarCancelacion(Pedido p) {
        if (p.stockDescontado && pedidos.marcarStockRepuesto(p.id)) reponer(p.items);
        if ("pendiente".equals(p.reembolsoEstado)) {
            boolean ok;
            try {
                ok = pasarela.reembolsar(p.referenciaCobro, p.total);
            } catch (RuntimeException e) {
                ok = false;
            }
            if (ok) {
                pedidos.fijarReembolso(p.id, "completado");
            } else {
                LOG.log(System.Logger.Level.WARNING, "Reembolso pendiente de conciliación para el pedido {0}", p.id);
            }
        }
    }

    // ═════════════ PUT /pedidos/{id}/estado ═════════════

    public Map<String, Object> cambiarEstado(Identidad quien, String pedidoId, String estadoTexto) {
        exigir(quien);
        if (!quien.esStaff()) throw ApiException.prohibido("Solo staff o admin pueden cambiar el estado de un pedido");
        EstadoPedido nuevo = EstadoPedido.deValor(estadoTexto);
        if (nuevo == null) throw ApiException.invalida("estado no válido");
        Pedido p = pedidos.porId(pedidoId).orElseThrow(() -> ApiException.noEncontrado("Pedido no encontrado"));
        if (!FlujoEstados.permitido(p.estado, nuevo)) {
            throw ApiException.conflicto("Transición no permitida: " + p.estado.valor() + " → " + nuevo.valor());
        }
        if (p.estado != nuevo) {
            if (!pedidos.cambiarEstadoSi(pedidoId, p.estado, nuevo)) {
                throw ApiException.conflicto("El pedido cambió de estado mientras tanto; vuelve a intentarlo");
            }
            p.estado = nuevo;
        }
        return Vistas.pedido(p);
    }

    // ═════════════ Eventos (memoria 3.4) ═════════════

    /** Reintenta la publicación de los eventos pendientes. Lo invoca un planificador. Devuelve cuántos se publicaron. */
    public int publicarPendientes() {
        int publicados = 0;
        for (Pedido p : pedidos.pendientesDeEvento(50)) {
            if (publicarYMarcar(p)) publicados++;
        }
        return publicados;
    }

    private boolean publicarYMarcar(Pedido p) {
        try {
            publicador.publicar(ROUTING_KEY_PEDIDO, evento(p));
            pedidos.marcarEventoPublicado(p.id);
            return true;
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo publicar el evento del pedido {0}; se reintentará: {1}", p.id, e.toString());
            return false;
        }
    }

    private static EventoCompra evento(Pedido p) {
        int unidades = p.items.stream().mapToInt(i -> i.cantidad).sum();
        return new EventoCompra(p.eventoId, p.usuarioId, p.id, p.fechaPedido.toString(), p.total,
                "Pedido de " + unidades + (unidades == 1 ? " artículo" : " artículos"));
    }

    // ═════════════ helpers ═════════════

    private void reponer(List<ItemCarrito> items) {
        for (ItemCarrito i : items) productos.reponerStock(i.productoId, i.talla, i.cantidad);
    }

    private static void propietarioOStaff(Identidad quien, Pedido p) {
        if (!p.usuarioId.equals(quien.usuarioId()) && !quien.esStaff()) throw ApiException.prohibido("Este pedido no es tuyo");
    }

    private static void exigir(Identidad quien) {
        if (quien == null) throw ApiException.noAutenticado("No autenticado");
    }

    /** Estable por pedido: si el evento se reenvía, Usuarios puede detectar el duplicado por este identificador. */
    static String idEvento(String pedidoId) {
        return UUID.nameUUIDFromBytes((ROUTING_KEY_PEDIDO + ":" + pedidoId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** UUID estable derivado de (usuario, clave): mismo usuario + misma clave = mismo pedido. */
    static String idEstable(String usuarioId, String clave) {
        return UUID.nameUUIDFromBytes((usuarioId + ":" + clave).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String huella(String direccion, String metodoPago) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((direccion + "\n" + metodoPago).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
