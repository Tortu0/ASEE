package com.asee.compras.persistencia;

import com.asee.compras.aplicacion.CarritoRepositorio;
import com.asee.compras.dominio.Carrito;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

public class CarritoMongo implements CarritoRepositorio {

    private static final String COLECCION = "carritos";
    private final MongoTemplate mongo;

    public CarritoMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public Carrito obtener(String usuarioId) {
        Carrito c = mongo.findById(usuarioId, Carrito.class, COLECCION);
        return c != null ? c : new Carrito(usuarioId);
    }

    @Override
    public void guardar(Carrito carrito) {
        mongo.save(carrito, COLECCION);
    }

    @Override
    public void vaciar(String usuarioId) {
        mongo.remove(Query.query(Criteria.where("_id").is(usuarioId)), Carrito.class, COLECCION);
    }
}
