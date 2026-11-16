package com.asee.estadisticas.config;

import com.asee.estadisticas.model.*;
import org.bson.Document;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class MongoConfig {
    @Bean
    TransactionTemplate transacciones(MongoDatabaseFactory factory) {
        return new TransactionTemplate(new MongoTransactionManager(factory));
    }
    @Bean
    ApplicationRunner prepararColecciones(MongoTemplate mongo, MongoMappingContext mapping) {
        return args -> {
            Document hello = mongo.getDb().runCommand(new Document("hello", 1));
            if (!hello.containsKey("setName")) throw new IllegalStateException("MongoDB requiere un replica set para las transacciones; consulta README.md");
            var resolver = new MongoPersistentEntityIndexResolver(mapping);
            for (var type : new Class<?>[]{EstadisticasPartidoDocumento.class, EventoEstadisticoDocumento.class, AjusteEstadisticoDocumento.class}) {
                if (!mongo.collectionExists(type)) {
                    try { mongo.createCollection(type); }
                    catch (org.springframework.dao.DataAccessException ex) { if (!mongo.collectionExists(type)) throw ex; }
                }
                for (var index : resolver.resolveIndexForEntity(mapping.getRequiredPersistentEntity(type))) {
                    mongo.indexOps(type).createIndex(index);
                }
            }
        };
    }
}
