package com.asee.compras.service;

import com.asee.compras.dto.PedidoRequest;
import com.asee.compras.exception.StockInsuficienteException;
import com.asee.compras.model.EstadoPedido;
import com.asee.compras.model.LineaPedido;
import com.asee.compras.model.Pedido;
import com.asee.compras.model.Producto;
import com.asee.compras.repository.PedidoRepository;
import com.asee.compras.repository.ProductoRepository;
import com.asee.compras.messaging.PedidoCreadoEvent;
import com.mongodb.client.result.UpdateResult;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ProductoRepository productoRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public PedidoService(PedidoRepository pedidoRepository,
                         ProductoRepository productoRepository,
                         MongoTemplate mongoTemplate,
                         ApplicationEventPublisher eventPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.productoRepository = productoRepository;
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
    }

    public List<Pedido> listar() {
        return pedidoRepository.findAll();
    }

    @Transactional
    public Pedido crear(PedidoRequest request) {
        if (request.lineas() == null || request.lineas().isEmpty()) {
            throw new IllegalArgumentException("El pedido no tiene líneas");
        }

        List<LineaPedido> lineas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (PedidoRequest.LineaRequest l : request.lineas()) {
            if (l.talla() == null || l.talla().isBlank()) {
                throw new IllegalArgumentException("Falta la talla en una línea");
            }
            if (l.cantidad() <= 0) {
                throw new IllegalArgumentException("La cantidad debe ser mayor que 0");
            }

            Producto producto = productoRepository.findById(l.productoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Producto no encontrado: " + l.productoId()));

            descontarStock(producto, l.talla(), l.cantidad());

            LineaPedido linea = new LineaPedido();
            linea.setProductoId(producto.getId());
            linea.setNombreProducto(producto.getNombre());
            linea.setTalla(l.talla());
            linea.setCantidad(l.cantidad());
            linea.setPrecioUnitario(producto.getPrecio());
            lineas.add(linea);

            total = total.add(producto.getPrecio().multiply(BigDecimal.valueOf(l.cantidad())));
        }

        Pedido pedido = new Pedido();
        pedido.setUsuarioId(request.usuarioId());
        pedido.setDireccionEnvio(request.direccionEnvio());
        pedido.setLineas(lineas);
        pedido.setTotal(total);
        pedido.setEstado(EstadoPedido.CREADO);
        pedido.setFecha(Instant.now());

        Pedido guardado = pedidoRepository.save(pedido);
        eventPublisher.publishEvent(PedidoCreadoEvent.from(guardado));
        return guardado;
    }

    // Resta solo si hay stock suficiente, en una única operación atómica
    private void descontarStock(Producto producto, String talla, int cantidad) {
        String campo = "stockPorTalla." + talla;

        Query query = new Query(Criteria.where("id").is(producto.getId())
                .and(campo).gte(cantidad));
        Update update = new Update().inc(campo, -cantidad);

        UpdateResult resultado = mongoTemplate.updateFirst(query, update, Producto.class);

        if (resultado.getModifiedCount() == 0) {
            throw new StockInsuficienteException("No hay stock suficiente de '"
                    + producto.getNombre() + "' en talla " + talla);
        }
    }
}