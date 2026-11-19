package com.asee.estadisticas;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClients;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Activa las pruebas de integracion cuando hay un MongoDB con replica set accesible
 * (el mismo que usan; ASEE_TEST_MONGO_URI permite indicar otro). Con ASEE_INTEGRATION=true
 * se activan siempre y fallan si Mongo no esta, como antes. Si no, se omiten con el motivo.
 */
public class MongoDisponible implements ExecutionCondition {
    @Override public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if ("true".equals(System.getenv("ASEE_INTEGRATION"))) return ConditionEvaluationResult.enabled("ASEE_INTEGRATION=true");
        String uri = System.getenv().getOrDefault("ASEE_TEST_MONGO_URI",
                "mongodb://localhost:27017/?replicaSet=asee-local&serverSelectionTimeoutMS=5000");
        var settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri))
                .applyToClusterSettings(b -> b.serverSelectionTimeout(2, TimeUnit.SECONDS)).build();
        try (var client = MongoClients.create(settings)) {
            var hello = client.getDatabase("admin").runCommand(new Document("hello", 1));
            if (hello.getString("setName") != null) return ConditionEvaluationResult.enabled("MongoDB con replica set disponible");
            return ConditionEvaluationResult.disabled("MongoDB sin replica set: pruebas de integracion omitidas");
        } catch (Exception ex) {
            return ConditionEvaluationResult.disabled("MongoDB no disponible: pruebas de integracion omitidas");
        }
    }
}
