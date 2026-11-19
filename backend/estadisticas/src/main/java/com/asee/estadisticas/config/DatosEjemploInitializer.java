package com.asee.estadisticas.config;

import com.asee.estadisticas.dto.EventoEstadisticoCreate;
import com.asee.estadisticas.exception.ApiException;
import com.asee.estadisticas.model.TipoEventoEstadistico;
import com.asee.estadisticas.repository.PartidoRepository;
import com.asee.estadisticas.service.EstadisticasService;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.datos-ejemplo", havingValue = "true")
public class DatosEjemploInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosEjemploInitializer.class);

    private static final List<UUID> PARTIDO_IDS = List.of(
            uuid("20000000", 1),
            uuid("20000000", 2),
            uuid("20000000", 3)
    );

    private static final List<UUID> JUGADOR_IDS = List.of(
            uuid("10000000", 1), uuid("10000000", 2), uuid("10000000", 3), uuid("10000000", 4),
            uuid("10000000", 5), uuid("10000000", 6), uuid("10000000", 7), uuid("10000000", 8)
    );

    private static final List<TipoEventoEstadistico> TIPOS = List.of(
            TipoEventoEstadistico.tiro_2_anotado, TipoEventoEstadistico.tiro_2_anotado, TipoEventoEstadistico.tiro_2_fallado,
            TipoEventoEstadistico.tiro_3_anotado, TipoEventoEstadistico.tiro_3_fallado,
            TipoEventoEstadistico.tiro_libre_anotado, TipoEventoEstadistico.tiro_libre_fallado,
            TipoEventoEstadistico.rebote_ofensivo, TipoEventoEstadistico.rebote_defensivo, TipoEventoEstadistico.rebote_defensivo,
            TipoEventoEstadistico.asistencia, TipoEventoEstadistico.robo, TipoEventoEstadistico.perdida,
            TipoEventoEstadistico.tapon, TipoEventoEstadistico.falta_personal
    );

    private final PartidoRepository partidos;
    private final EstadisticasService estadisticas;

    public DatosEjemploInitializer(PartidoRepository partidos, EstadisticasService estadisticas) {
        this.partidos = partidos;
        this.estadisticas = estadisticas;
    }

    @Override
    public void run(String... args) {
        Thread cargador = new Thread(this::cargarConEspera, "datos-ejemplo-estadisticas");
        cargador.setDaemon(true);
        cargador.start();
    }

    private void cargarConEspera() {
        try {
            if (partidos.count() > 0) {
                log.info("Estadísticas: la colección ya tiene datos, no se carga nada");
                return;
            }
            if (!esperarDeportivo()) {
                log.warn("Estadísticas: Deportivo no respondió a tiempo; se omite la precarga de ejemplo");
                return;
            }
            int creados = 0;
            for (int pi = 0; pi < PARTIDO_IDS.size(); pi++) {
                UUID partido = PARTIDO_IDS.get(pi);
                if (!registrarSeguro(partido, crearEvento("demo-inicio", TipoEventoEstadistico.inicio_partido, null, 1, 600))) {
                    continue;
                }
                creados++;
                for (int ji = 0; ji < JUGADOR_IDS.size(); ji++) {
                    UUID jugador = JUGADOR_IDS.get(ji);
                    Random rnd = new Random((pi + 1) * 100L + ji);
                    int numEventos = 14 + rnd.nextInt(9);
                    int faltas = 0;
                    for (int n = 1; n <= numEventos; n++) {
                        TipoEventoEstadistico tipo = TIPOS.get(rnd.nextInt(TIPOS.size()));
                        if (tipo == TipoEventoEstadistico.falta_personal) {
                            if (faltas >= 4) {
                                tipo = TipoEventoEstadistico.rebote_defensivo;
                            } else {
                                faltas++;
                            }
                        }
                        int periodo = Math.min(4, 1 + ((n - 1) * 4) / numEventos);
                        int segundos = 30 + rnd.nextInt(570);
                        if (registrarSeguro(partido, crearEvento("demo-" + jugador + "-" + n, tipo, jugador, periodo, segundos))) {
                            creados++;
                        }
                    }
                }
                registrarSeguro(partido, crearEvento("demo-fin", TipoEventoEstadistico.fin_partido, null, 4, 0));
            }
            log.info("Estadísticas: cargados {} eventos de ejemplo en {} partidos", creados, PARTIDO_IDS.size());
        } catch (Exception e) {
            log.warn("No se han cargado las estadísticas de ejemplo: {}", e.getMessage());
        }
    }

    private boolean esperarDeportivo() throws InterruptedException {
        for (int intento = 0; intento < 20; intento++) {
            try {
                estadisticas.estado(PARTIDO_IDS.get(0));
                return true;
            } catch (ApiException ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("no encontrado")) {
                    return true;
                }
            } catch (Exception ignored) {
            }
            Thread.sleep(2000);
        }
        return false;
    }

    private boolean registrarSeguro(UUID partido, EventoEstadisticoCreate dto) {
        try {
            estadisticas.registrar(partido, dto);
            return true;
        } catch (ApiException ex) {
            return false;
        }
    }

    private static EventoEstadisticoCreate crearEvento(String clientId, TipoEventoEstadistico tipo, UUID jugadorId, int periodo, int segundos) {
        EventoEstadisticoCreate dto = new EventoEstadisticoCreate();
        dto.setClientEventoId(clientId);
        dto.setTipo(tipo);
        dto.setJugadorId(jugadorId);
        dto.setPeriodo(periodo);
        dto.setSegundosRestantes(segundos);
        return dto;
    }

    private static UUID uuid(String prefijo, int n) {
        return UUID.fromString(String.format("%s-0000-0000-0000-%012d", prefijo, n));
    }
}
