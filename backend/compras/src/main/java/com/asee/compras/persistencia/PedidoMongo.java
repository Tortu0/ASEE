package com.asee.compras.persistencia;

import com.asee.compras.aplicacion.PedidoRepositorio;
import com.asee.compras.dominio.EstadoPedido;
import com.asee.compras.dominio.Pedido;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

/**
 * Pedidos en MongoDB. El _id del pedido se deriva de (usuario, Idempotency-Key), así que la propia clave primaria
 * impide que dos peticiones concurrentes con la misma clave creen dos pedidos. Los cambios puntuales usan
 * operaciones atómicas de un solo documento para no pisar modificaciones concurrentes (cancelaciones, estados...).
 */
public class PedidoMongo implements PedidoRepositorio {

    private static final String COLECCION = "pedidos";
    private static final int MAXIMO_LISTADO = 500;
    private final MongoTemplate mongo;

    public PedidoMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    private static Query consultaPorId(String id) {
        return new Query(Criteria.where("_id").is(id));
    }

    @Override
    public Optional<Pedido> porId(String id) {
        return Optional.ofNullable(mongo.findById(id, Pedido.class, COLECCION));
    }

    @Override
    public boolean insertar(Pedido pedido) {
        try {
            mongo.insert(pedido, COLECCION);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public void guardar(Pedido pedido) {
        mongo.save(pedido, COLECCION);
    }

    @Override
    public void eliminar(String id) {
        mongo.remove(consultaPorId(id), Pedido.class, COLECCION);
    }

    @Override
    public List<Pedido> listar(String usuarioId, EstadoPedido estado) {
        Query q = new Query();
        if (usuarioId != null) q.addCriteria(Criteria.where("usuarioId").is(usuarioId));
        if (estado != null) q.addCriteria(Criteria.where("estado").is(estado));
        q.with(Sort.by(Sort.Direction.DESC, "fechaPedido")).limit(MAXIMO_LISTADO);
        return mongo.find(q, Pedido.class, COLECCION);
    }

    @Override
    public Optional<Pedido> cancelarSiCancelable(String id) {
        Query q = new Query(Criteria.where("_id").is(id).and("estado").in(EstadoPedido.PAGADO, EstadoPedido.PREPARANDO));
        Update u = new Update().set("estado", EstadoPedido.CANCELADO).set("reembolsoEstado", "pendiente");
        return Optional.ofNullable(mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), Pedido.class, COLECCION));
    }

    @Override
    public boolean cambiarEstadoSi(String id, EstadoPedido desde, EstadoPedido hasta) {
        Query q = new Query(Criteria.where("_id").is(id).and("estado").is(desde));
        return mongo.updateFirst(q, new Update().set("estado", hasta), Pedido.class, COLECCION).getModifiedCount() == 1;
    }

    @Override
    public boolean marcarStockRepuesto(String id) {
        Query q = new Query(Criteria.where("_id").is(id).and("stockRepuesto").is(false));
        return mongo.updateFirst(q, new Update().set("stockRepuesto", true), Pedido.class, COLECCION).getModifiedCount() == 1;
    }

    @Override
    public void fijarReembolso(String id, String estado) {
        mongo.updateFirst(consultaPorId(id), new Update().set("reembolsoEstado", estado), Pedido.class, COLECCION);
    }

    @Override
    public boolean marcarEventoPublicado(String id) {
        Query q = new Query(Criteria.where("_id").is(id).and("eventoPublicado").is(false));
        return mongo.updateFirst(q, new Update().set("eventoPublicado", true), Pedido.class, COLECCION).getModifiedCount() == 1;
    }

    @Override
    public List<Pedido> pendientesDeEvento(int max) {
        Query q = new Query(Criteria.where("eventoId").ne(null).and("eventoPublicado").is(false)).limit(max);
        return mongo.find(q, Pedido.class, COLECCION);
    }
}
