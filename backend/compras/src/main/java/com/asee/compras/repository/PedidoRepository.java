package com.asee.compras.repository;

import com.asee.compras.model.Pedido;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PedidoRepository extends MongoRepository<Pedido, String> {
}