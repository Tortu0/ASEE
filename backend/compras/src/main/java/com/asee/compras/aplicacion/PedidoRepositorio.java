package com.asee.compras.aplicacion;

import com.asee.compras.dominio.EstadoPedido;
import com.asee.compras.dominio.Pedido;

import java.util.List;
import java.util.Optional;

public interface PedidoRepositorio {
    Optional<Pedido> porId(String id);

    /** Inserta un pedido nuevo; false (sin cambios) si ya existe uno con ese id. */
    boolean insertar(Pedido pedido);

    /** Reemplaza el documento completo. Solo para pedidos que nadie más modifica a la vez (fase de pago). */
    void guardar(Pedido pedido);

    void eliminar(String id);

    /** Más recientes primero. usuarioId/estado pueden ser null (sin filtrar). */
    List<Pedido> listar(String usuarioId, EstadoPedido estado);

    // Cambios puntuales y atómicos (evitan pisar modificaciones concurrentes):

    /** pagado|preparando -> cancelado, dejando el reembolso como "pendiente". Vacío si el estado ya no lo permite. */
    Optional<Pedido> cancelarSiCancelable(String id);

    boolean cambiarEstadoSi(String id, EstadoPedido desde, EstadoPedido hasta);

    /** true solo para quien consigue marcarlo: garantiza que el stock se repone una sola vez. */
    boolean marcarStockRepuesto(String id);

    void fijarReembolso(String id, String estado);

    boolean marcarEventoPublicado(String id);

    /** Pedidos pagados cuyo evento aún no se ha publicado. */
    List<Pedido> pendientesDeEvento(int max);
}
