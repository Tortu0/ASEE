package com.asee.estadisticas.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import static org.assertj.core.api.Assertions.*;

class DocumentosMongoTest {
    private MappingMongoConverter converter;
    private MongoMappingContext mapping;
    private final UUID partido = UUID.randomUUID();
    private final UUID jugador = UUID.randomUUID();
    private final Instant instante = Instant.parse("2026-11-04T18:30:00Z");

    @BeforeEach
    void configurarConversorRealSinServidor() throws Exception {
        var conversions = MongoCustomConversions.create(adapter -> {});
        mapping = new MongoMappingContext();
        mapping.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        mapping.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, mapping);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
    }

    private <T> T roundTrip(T value, Class<T> type) {
        Document stored = new Document();
        converter.write(value, stored);
        assertThat(stored.get("_id")).isInstanceOf(UUID.class);
        return converter.read(type, stored);
    }

    private EstadoEstadisticoJugador estadoConCorreccion() {
        return new EstadoEstadisticoJugador(jugador, ContadoresEstadisticos.ceros(),
                new ContadoresEstadisticos(-2, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                new DatosManualesJugador(true, 620, -4));
    }

    @Test
    void conservaAjustesNegativosDatosManualesYVersionesSeparadas() {
        var original = new EstadisticasPartidoDocumento(partido, "2026-2027", "Liga", 1, instante,
                EstadoSeguimientoPartido.en_directo, 2, 400, List.of(estadoConCorreccion()), instante, 5, 3L);
        var recovered = roundTrip(original, EstadisticasPartidoDocumento.class);
        assertThat(recovered).isEqualTo(original);
        assertThat(recovered.jugadores().getFirst().ajustes().puntos()).isEqualTo(-2);
        assertThat(recovered.jugadores().getFirst().manuales().segundosJugados()).isEqualTo(620);
    }

    @Test
    void conservaEventoAnuladoParaAuditoria() {
        var original = new EventoEstadisticoDocumento(UUID.randomUUID(), partido, "tablet-1", TipoEventoEstadistico.tiro_2_anotado,
                jugador, null, null, 1, 500, null, null, instante, true, instante.plusSeconds(1));
        assertThat(roundTrip(original, EventoEstadisticoDocumento.class)).isEqualTo(original);
    }

    @Test
    void conservaAutorCamposYEstadosDeUnaCorreccion() {
        var original = new AjusteEstadisticoDocumento(UUID.randomUUID(), partido, jugador, UUID.randomUUID(), instante, 5,
                Set.of(CampoEditableEstadistica.puntos, CampoEditableEstadistica.tiros2Anotados), null, estadoConCorreccion());
        assertThat(roundTrip(original, AjusteEstadisticoDocumento.class)).isEqualTo(original);
    }

    @Test
    void indiceUnicoDeEventoIncluyePartidoParaPermitirMismoIdEnOtroPartido() {
        var entity = mapping.getRequiredPersistentEntity(EventoEstadisticoDocumento.class);
        var indices = new MongoPersistentEntityIndexResolver(mapping).resolveIndexForEntity(entity);
        var unique = indices.stream().filter(i -> Boolean.TRUE.equals(i.getIndexOptions().getBoolean("unique"))).toList();
        assertThat(unique).hasSize(1);
        assertThat(unique.getFirst().getIndexKeys()).containsExactlyInAnyOrderEntriesOf(
                new Document("partidoId", 1).append("clientEventoId", 1));
    }
}
