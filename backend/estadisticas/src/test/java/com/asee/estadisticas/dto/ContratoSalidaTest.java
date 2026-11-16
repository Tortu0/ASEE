package com.asee.estadisticas.dto;

import com.asee.estadisticas.config.JsonConfig;
import jakarta.validation.Validator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("unchecked")
class ContratoSalidaTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(JsonConfig.class);

    private Map<String, Map<String, Object>> schemas() throws Exception {
        Map<String, Object> contract = new Yaml().load(Files.readString(Path.of("docs/openapi/estadisticas.yaml")));
        return (Map<String, Map<String, Object>>) ((Map<String, Object>) contract.get("components")).get("schemas");
    }

    private Map<String, Object> properties(Map<String, Object> schema, Map<String, Map<String, Object>> schemas) {
        if (schema.containsKey("$ref")) return properties(schemas.get(refName(schema)), schemas);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map<String, Object> part : (List<Map<String, Object>>) schema.getOrDefault("allOf", List.of())) {
            result.putAll(properties(part, schemas));
        }
        result.putAll((Map<String, Object>) schema.getOrDefault("properties", Map.of()));
        return result;
    }

    private String refName(Map<String, Object> schema) {
        String ref = (String) schema.get("$ref");
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private Object sample(Map<String, Object> schema, Map<String, Map<String, Object>> schemas) {
        if (schema.containsKey("$ref")) return sample(schemas.get(refName(schema)), schemas);
        if (schema.containsKey("enum")) return ((List<?>) schema.get("enum")).getFirst();
        if (schema.containsKey("allOf") && !schema.containsKey("properties")
                && ((List<?>)schema.get("allOf")).size() == 1) {
            return sample(((List<Map<String,Object>>)schema.get("allOf")).getFirst(), schemas);
        }
        if (schema.containsKey("properties") || schema.containsKey("allOf")) {
            Map<String, Object> result = new LinkedHashMap<>();
            properties(schema, schemas).forEach((key, value) -> result.put(key, sample((Map<String,Object>)value, schemas)));
            return result;
        }
        return switch ((String)schema.getOrDefault("type", "object")) {
            case "array" -> List.of(sample((Map<String,Object>)schema.get("items"), schemas));
            case "integer" -> schema.getOrDefault("minimum", 0);
            case "number" -> 0.0;
            case "boolean" -> false;
            case "string" -> switch ((String)schema.getOrDefault("format", "")) {
                case "uuid" -> "550e8400-e29b-41d4-a716-446655440000";
                case "date-time" -> "2026-11-04T18:30:00Z";
                default -> "ejemplo";
            };
            default -> throw new IllegalStateException("Esquema no cubierto: " + schema);
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {"Error", "EventoEstadistico", "EstadisticaJugadorPartidoBase", "EstadisticaJugadorPartido",
            "EstadisticasPartido", "PorcentajesTiro", "MetricasAvanzadas", "TotalesTemporada", "PromediosTemporada",
            "EstadisticasJugadorTemporada", "PosicionRanking", "RankingEstadistico"})
    void respuestasMantienenNombresTiposYCamposPlanosDelYaml(String name) throws Exception {
        var schemas = schemas();
        var example = sample(schemas.get(name), schemas);
        Class<?> type = Class.forName("com.asee.estadisticas.dto." + (name.equals("Error") ? "ErrorResponse" : name));
        runner.run(context -> {
            var mapper = context.getBean(JsonMapper.class);
            Object dto = mapper.readValue(mapper.writeValueAsString(example), type);
            assertThat(context.getBean(Validator.class).validate(dto)).isEmpty();
            Map<String, Object> output = mapper.readValue(mapper.writeValueAsString(dto), Map.class);
            assertThat(output.keySet()).containsExactlyInAnyOrderElementsOf(properties(schemas.get(name), schemas).keySet());
            assertThat(output).doesNotContainKeys("_id", "revision", "versionPublica", "ajustes", "sumaEventos");
            if (output.containsKey("fechaHora")) assertThat(output.get("fechaHora")).isEqualTo("2026-11-04T18:30:00Z");
            if (output.containsKey("jugadorId")) assertThat(output.get("jugadorId")).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"EstadoSeguimientoPartido", "TipoEventoEstadistico", "TipoFalta", "MetricaRanking"})
    void enumeracionesCoincidenExactamenteConElContrato(String name) throws Exception {
        Class<?> type = Class.forName("com.asee.estadisticas.model." + name);
        List<String> actual = Arrays.stream(type.getEnumConstants()).map(Object::toString).toList();
        assertThat(actual).containsExactlyElementsOf((List<String>)schemas().get(name).get("enum"));
    }

    @Test
    void metricasIndefinidasSonNullYDetalleOpcionalSeOmite() throws Exception {
        var schemas = schemas();
        Map<String, Object> example = (Map<String,Object>)sample(schemas.get("EstadisticasJugadorTemporada"), schemas);
        example.remove("detallePartidos");
        runner.run(context -> {
            var mapper = context.getBean(JsonMapper.class);
            assertThat(mapper.writeValueAsString(new MetricasAvanzadas(null, null, null)))
                    .contains("\"porcentajeTiroEfectivo\":null", "\"trueShooting\":null", "\"ratioAsistenciasPerdidas\":null");
            var dto = mapper.readValue(mapper.writeValueAsString(example), EstadisticasJugadorTemporada.class);
            assertThat(mapper.writeValueAsString(dto)).doesNotContain("detallePartidos");
        });
    }
}
