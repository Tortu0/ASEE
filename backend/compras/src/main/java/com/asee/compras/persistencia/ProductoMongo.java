package com.asee.compras.persistencia;

import com.asee.compras.aplicacion.ProductoRepositorio;
import com.asee.compras.dominio.Producto;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Optional;

public class ProductoMongo implements ProductoRepositorio {

    private static final String COLECCION = "productos";
    private final MongoTemplate mongo;

    public ProductoMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public List<Producto> listar(String categoria, String jugadorId, String patrocinadorId, int pagina, int tamano) {
        Query q = new Query();
        if (categoria != null) q.addCriteria(Criteria.where("categoria").is(categoria));
        if (jugadorId != null) q.addCriteria(Criteria.where("jugadorId").is(jugadorId));
        if (patrocinadorId != null) q.addCriteria(Criteria.where("patrocinadorId").is(patrocinadorId));
        q.with(Sort.by("nombre")).skip((long) (pagina - 1) * tamano).limit(tamano);
        return mongo.find(q, Producto.class, COLECCION);
    }

    @Override
    public Optional<Producto> porId(String id) {
        return Optional.ofNullable(mongo.findById(id, Producto.class, COLECCION));
    }

    @Override
    public Producto guardar(Producto producto) {
        return mongo.save(producto, COLECCION);
    }

    @Override
    public boolean eliminar(String id) {
        return mongo.remove(Query.query(Criteria.where("_id").is(id)), Producto.class, COLECCION).getDeletedCount() > 0;
    }

    /** Una sola operación atómica en MongoDB: solo descuenta si la talla existe y tiene unidades suficientes. */
    @Override
    public boolean descontarStock(String productoId, String talla, int unidades) {
        Query q = new Query(Criteria.where("_id").is(productoId)
                .and("stock").elemMatch(Criteria.where("talla").is(talla).and("unidades").gte(unidades)));
        Update u = new Update().inc("stock.$.unidades", -unidades);
        return mongo.updateFirst(q, u, Producto.class, COLECCION).getModifiedCount() == 1;
    }

    @Override
    public void reponerStock(String productoId, String talla, int unidades) {
        Query q = new Query(Criteria.where("_id").is(productoId).and("stock.talla").is(talla));
        mongo.updateFirst(q, new Update().inc("stock.$.unidades", unidades), Producto.class, COLECCION);
    }
}
