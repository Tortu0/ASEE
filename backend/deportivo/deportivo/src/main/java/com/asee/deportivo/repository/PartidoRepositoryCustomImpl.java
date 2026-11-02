package com.asee.deportivo.repository;

import com.asee.deportivo.model.EstadoPartido;
import com.asee.deportivo.model.Partido;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class PartidoRepositoryCustomImpl implements PartidoRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    public PartidoRepositoryCustomImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Partido> buscar(EstadoPartido estado,
                                Instant desde,
                                Instant hasta,
                                Boolean ventaAbierta,
                                int pagina,
                                int tamano) {

        List<Criteria> filtros = new ArrayList<>();
        if (estado != null) {
            filtros.add(Criteria.where("estado").is(estado));
        }
        if (desde != null) {
            filtros.add(Criteria.where("fechaHora").gte(desde));
        }
        if (hasta != null) {
            filtros.add(Criteria.where("fechaHora").lt(hasta));
        }
        if (ventaAbierta != null) {
            filtros.add(Criteria.where("ventaEntradasAbierta").is(ventaAbierta));
        }

        Query query = new Query();
        if (!filtros.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filtros));
        }
        query.with(Sort.by("fechaHora").ascending());
        query.skip((long) (pagina - 1) * tamano).limit(tamano);

        return mongoTemplate.find(query, Partido.class);
    }
}