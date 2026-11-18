package com.asee.estadisticas.dto;

import com.asee.estadisticas.config.JsonConfig;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

class ContratoEntradaTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(JsonConfig.class);
    private static final String JUGADOR = "550e8400-e29b-41d4-a716-446655440000";

    @Test
    void actualizacionDistingueOmitidosDeCeroYFalse() {
        runner.run(context -> {
            var mapper = context.getBean(JsonMapper.class);
            var dto = mapper.readValue("{\"puntos\":0,\"titular\":false,\"masMenos\":-8}", EstadisticaJugadorPartidoUpdate.class);
            assertThat(dto.getPuntos()).isZero();
            assertThat(dto.getTitular()).isFalse();
            assertThat(dto.getMasMenos()).isEqualTo(-8);
            assertThat(dto.getAsistencias()).isNull();
            assertThat(context.getBean(Validator.class).validate(dto)).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"otraPropiedad\":1}", "{\"puntos\":-1}", "{\"faltasCometidas\":6}", "{\"segundosJugados\":-1}"})
    void rechazaActualizacionesVaciasOContadoresInvalidos(String json) {
        runner.run(context -> {
            var dto = context.getBean(JsonMapper.class).readValue(json, EstadisticaJugadorPartidoUpdate.class);
            assertThat(context.getBean(Validator.class).validate(dto)).isNotEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"puntos\":null}", "{\"titular\":null}", "{\"puntos\":1.5}", "{\"puntos\":\"3\"}",
            "{\"titular\":\"false\"}", "{\"puntos\":2,\"rebotesTotales\":1}", "{\"valoracion\":null}",
            "{\"tirosCampoAnotados\":0}", "{\"tirosCampoIntentados\":0}", "{\"porcentajes\":{}}", "{\"metricasAvanzadas\":{}}"})
    void rechazaNulosTiposIncorrectosYCamposCalculados(String json) {
        runner.run(context -> assertThatThrownBy(() -> context.getBean(JsonMapper.class)
                .readValue(json, EstadisticaJugadorPartidoUpdate.class)).isInstanceOf(RuntimeException.class));
    }

    @Test
    void coherenciaDeTirosSeEvaluaraAlMezclarConElEstadoGuardado() {
        runner.run(context -> {
            var dto = context.getBean(JsonMapper.class).readValue("{\"tiros2Anotados\":4}", EstadisticaJugadorPartidoUpdate.class);
            assertThat(context.getBean(Validator.class).validate(dto)).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"inicio_partido", "inicio_periodo", "fin_periodo", "fin_partido"})
    void eventosGeneralesNoRequierenJugador(String tipo) {
        runner.run(context -> {
            var dto = context.getBean(JsonMapper.class).readValue(
                    "{\"clientEventoId\":\"e1\",\"tipo\":\"" + tipo + "\",\"periodo\":1}", EventoEstadisticoCreate.class);
            assertThat(context.getBean(Validator.class).validate(dto)).isEmpty();
            assertThat(dto.getSegundosRestantes()).isNull();
            assertThat(dto.getTipoFalta()).isNull();
        });
    }

    @Test
    void eventoIndividualRequiereJugadorYCamposObligatorios() {
        runner.run(context -> {
            var mapper = context.getBean(JsonMapper.class);
            var validator = context.getBean(Validator.class);
            String json = "{\"clientEventoId\":\"e1\",\"tipo\":\"asistencia\",\"periodo\":1}";
            assertThat(validator.validate(mapper.readValue(json, EventoEstadisticoCreate.class))).isNotEmpty();
            json = json.replace("}", ",\"jugadorId\":\"" + JUGADOR + "\"}");
            assertThat(validator.validate(mapper.readValue(json, EventoEstadisticoCreate.class))).isEmpty();
            assertThat(validator.validate(mapper.readValue("{}", EventoEstadisticoCreate.class))).hasSize(3);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"tipoFalta\":null", "\"segundosRestantes\":null", "\"tipo\":0",
            "\"tipo\":\"ASISTENCIA\"", "\"jugadorId\":\"no-es-uuid\"", "\"periodo\":1.2"})
    void eventoRechazaNulosNoPermitidosYTiposInvalidos(String propiedad) {
        runner.run(context -> assertThatThrownBy(() -> context.getBean(JsonMapper.class)
                .readValue("{" + propiedad + "}", EventoEstadisticoCreate.class)).isInstanceOf(RuntimeException.class));
    }

    @Test
    void eventoRespetaLimitesYNulosPermitidos() {
        runner.run(context -> {
            var mapper = context.getBean(JsonMapper.class);
            var validator = context.getBean(Validator.class);
            String json = """
                    {"clientEventoId":"e1","tipo":"inicio_partido","periodo":10,"segundosRestantes":600,
                     "jugadorId":null,"jugadorRelacionadoId":null,"jugadaId":null,"observaciones":null}
                    """;
            assertThat(validator.validate(mapper.readValue(json, EventoEstadisticoCreate.class))).isEmpty();
            var dto = mapper.readValue(json, EventoEstadisticoCreate.class);
            dto.setPeriodo(11);
            dto.setSegundosRestantes(601);
            dto.setClientEventoId("x".repeat(101));
            dto.setObservaciones("x".repeat(501));
            dto.setJugadaId("x".repeat(101));
            assertThat(validator.validate(dto)).hasSize(5);
        });
    }

    @Test
    void partidoDistingueRelojOmitidoDeNullExplicito() {
        runner.run(context -> {
            var mapper = context.getBean(JsonMapper.class);
            String jugadores = "\"jugadores\":[{\"jugadorId\":\"" + JUGADOR + "\",\"estadisticas\":{\"puntos\":0}}]";
            var omitido = mapper.readValue("{" + jugadores + "}", EstadisticasPartidoUpdate.class);
            var nulo = mapper.readValue("{\"periodoActual\":null,\"segundosRestantesPeriodo\":null," + jugadores + "}", EstadisticasPartidoUpdate.class);
            assertThat(omitido.tienePeriodoActual()).isFalse();
            assertThat(omitido.tieneSegundosRestantesPeriodo()).isFalse();
            assertThat(nulo.tienePeriodoActual()).isTrue();
            assertThat(nulo.tieneSegundosRestantesPeriodo()).isTrue();
            assertThat(nulo.getPeriodoActual()).isNull();
            assertThat(context.getBean(Validator.class).validate(nulo)).isEmpty();
        });
    }

    @Test
    void partidoRechazaJugadoresRepetidosYValidaEstadisticasAnidadas() {
        runner.run(context -> {
            var mapper = context.getBean(JsonMapper.class);
            var validator = context.getBean(Validator.class);
            String jugador = "{\"jugadorId\":\"" + JUGADOR + "\",\"estadisticas\":{\"puntos\":0}}";
            var repetidos = mapper.readValue("{\"jugadores\":[" + jugador + "," + jugador + "]}", EstadisticasPartidoUpdate.class);
            assertThat(validator.validate(repetidos)).isNotEmpty();
            var vacio = mapper.readValue("{\"jugadores\":[" + jugador.replace("\"puntos\":0", "") + "]}", EstadisticasPartidoUpdate.class);
            assertThat(validator.validate(vacio)).isNotEmpty();
            assertThat(validator.validate(mapper.readValue("{\"jugadores\":[]}", EstadisticasPartidoUpdate.class))).isNotEmpty();
            assertThat(validator.validate(mapper.readValue("{}", EstadisticasPartidoUpdate.class))).isNotEmpty();
            assertThatThrownBy(() -> mapper.readValue("{\"jugadores\":[null]}", EstadisticasPartidoUpdate.class)).isInstanceOf(RuntimeException.class);
        });
    }
}
