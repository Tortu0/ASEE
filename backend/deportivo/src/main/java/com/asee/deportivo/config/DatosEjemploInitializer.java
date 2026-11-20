package com.asee.deportivo.config;

import com.asee.deportivo.model.CategoriaNoticia;
import com.asee.deportivo.model.EstadoPartido;
import com.asee.deportivo.model.Jugador;
import com.asee.deportivo.model.Noticia;
import com.asee.deportivo.model.Partido;
import com.asee.deportivo.model.Posicion;
import com.asee.deportivo.repository.JugadorRepository;
import com.asee.deportivo.repository.NoticiaRepository;
import com.asee.deportivo.repository.PartidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.datos-ejemplo", havingValue = "true")
public class DatosEjemploInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosEjemploInitializer.class);

    private static final String TEMPORADA = "2026-2027";
    private static final String COMPETICION = "Liga regional";
    private static final String PABELLON_LOCAL = "Pabellón Municipal de Madroñera";

    private final JugadorRepository jugadores;
    private final PartidoRepository partidos;
    private final NoticiaRepository noticias;

    @Value("${SUPABASE_URL:}")
    private String supabaseUrl;

    public DatosEjemploInitializer(JugadorRepository jugadores,
                                   PartidoRepository partidos,
                                   NoticiaRepository noticias) {
        this.jugadores = jugadores;
        this.partidos = partidos;
        this.noticias = noticias;
    }

    @Override
    public void run(String... args) {
        try {
            cargarJugadores();
            cargarPartidos();
            cargarNoticias();
        } catch (Exception e) {
            // Si MongoDB no está disponible, la aplicación sigue arrancando
            log.warn("No se han cargado los datos de ejemplo: {}", e.getMessage());
        }
    }

    private void cargarJugadores() {
        if (jugadores.count() > 0) {
            log.info("Jugadores: la colección ya tiene datos, no se carga nada");
            return;
        }
        jugadores.saveAll(List.of(
                new Jugador(jugadorId(1), "Carlos Gómez", 4, Posicion.base, 185,
                        LocalDate.of(1999, 3, 12), fotoJugador("04-CarlosGomez.png"), true),
                new Jugador(jugadorId(2), "Pablo Sánchez", 7, Posicion.escolta, 190,
                        LocalDate.of(2000, 7, 25), fotoJugador("07-PabloSanchez.png"), true),
                new Jugador(jugadorId(3), "Luis Pérez", 9, Posicion.alero, 198,
                        LocalDate.of(1998, 11, 2), fotoJugador("09-LuisPerez.png"), true),
                new Jugador(jugadorId(4), "Diego Martín", 11, Posicion.alero, 196,
                        LocalDate.of(2001, 1, 18), fotoJugador("11-DiegoMartin.png"), true),
                new Jugador(jugadorId(5), "Javier Ramos", 13, Posicion.ala_pivot, 203,
                        LocalDate.of(1997, 5, 30), fotoJugador("13-JavierRamos.png"), true),
                new Jugador(jugadorId(6), "Álvaro Moreno", 15, Posicion.pivot, 208,
                        LocalDate.of(1996, 9, 9), fotoJugador("15-AlvaroMoreno.png"), true),
                new Jugador(jugadorId(7), "Marcos Ortega", 5, Posicion.base, 180,
                        LocalDate.of(2002, 4, 14), fotoJugador("05-MarcosOrtega.png"), true),
                new Jugador(jugadorId(8), "Hugo Blanco", 8, Posicion.escolta, 188,
                        LocalDate.of(2003, 8, 21), fotoJugador("08-HugoBlanco.png"), true),
                // Jugador dado de baja: su dorsal no cuenta para la regla de unicidad
                new Jugador(jugadorId(9), "Iván Castro", 10, Posicion.alero, 194,
                        LocalDate.of(1995, 12, 5), null, false)
        ));
        log.info("Jugadores: cargados 9 jugadores de ejemplo");
    }

    private void cargarPartidos() {
        if (partidos.count() > 0) {
            log.info("Partidos: la colección ya tiene datos, no se carga nada");
            return;
        }
        // puntosLocal / puntosVisitante son los del equipo que juega en casa en ese partido
        partidos.saveAll(List.of(
                new Partido(partidoId(1), Instant.parse("2026-10-10T18:30:00Z"),
                        "CB Navalmoral", PABELLON_LOCAL, true, TEMPORADA, COMPETICION, 1,
                        EstadoPartido.finalizado, 78, 71, false),
                new Partido(partidoId(2), Instant.parse("2026-10-17T17:00:00Z"),
                        "CB Coria", "Pabellón Municipal de Coria", false, TEMPORADA, COMPETICION, 2,
                        EstadoPartido.finalizado, 65, 70, false),
                new Partido(partidoId(3), Instant.parse("2026-10-24T18:30:00Z"),
                        "Plasencia Basket", PABELLON_LOCAL, true, TEMPORADA, COMPETICION, 3,
                        EstadoPartido.finalizado, 82, 80, false),
                new Partido(partidoId(4), Instant.parse("2026-11-07T18:30:00Z"),
                        "CB Jaraíz", PABELLON_LOCAL, true, TEMPORADA, COMPETICION, 4,
                        EstadoPartido.programado, null, null, true),
                new Partido(partidoId(5), Instant.parse("2026-11-14T17:00:00Z"),
                        "Trujillo Basket", "Pabellón Municipal de Trujillo", false, TEMPORADA, COMPETICION, 5,
                        EstadoPartido.programado, null, null, false),
                new Partido(partidoId(6), Instant.parse("2026-11-21T18:30:00Z"),
                        "CB Moraleja", PABELLON_LOCAL, true, TEMPORADA, COMPETICION, 6,
                        EstadoPartido.aplazado, null, null, false),
                new Partido(partidoId(7), Instant.parse("2026-11-28T18:30:00Z"),
                        "CB Cáceres Joven", PABELLON_LOCAL, true, TEMPORADA, COMPETICION, 7,
                        EstadoPartido.programado, null, null, true)
        ));
        log.info("Partidos: cargados 7 partidos de ejemplo");
    }

    private void cargarNoticias() {
        if (noticias.count() > 0) {
            log.info("Noticias: la colección ya tiene datos, no se carga nada");
            return;
        }
        // autorId queda a null: todavía no hay usuarios staff con los que relacionarlas
        noticias.saveAll(List.of(
                new Noticia(noticiaId(1),
                        "El club incorpora al base Marcos Ortega",
                        "El club ha cerrado el fichaje del base Marcos Ortega, que llega para reforzar la rotación exterior esta temporada.",
                        CategoriaNoticia.fichaje, List.of(jugadorId(7)), null, null,
                        Instant.parse("2026-09-28T10:00:00Z")),
                new Noticia(noticiaId(2),
                        "Victoria en el debut de liga frente al CB Navalmoral",
                        "El equipo se impuso por 78-71 en el Pabellón Municipal, con un gran partido de Carlos Gómez y Luis Pérez.",
                        CategoriaNoticia.cronica, List.of(jugadorId(1), jugadorId(3)), null, null,
                        Instant.parse("2026-10-11T09:00:00Z")),
                new Noticia(noticiaId(3),
                        "Javier Ramos, baja dos semanas por un esguince",
                        "El ala-pívot Javier Ramos se perderá los próximos encuentros por un esguince de tobillo.",
                        CategoriaNoticia.lesion, List.of(jugadorId(5)), null, null,
                        Instant.parse("2026-10-25T12:00:00Z")),
                new Noticia(noticiaId(4),
                        "Convocatoria para el partido ante el CB Jaraíz",
                        "El cuerpo técnico ha hecho pública la lista de convocados para el partido de la jornada 4.",
                        CategoriaNoticia.convocatoria, List.of(), null, null,
                        Instant.parse("2026-10-29T16:00:00Z"))
        ));
        log.info("Noticias: cargadas 4 noticias de ejemplo");
    }

    // ---------- UUID fijos: iguales en todos los ordenadores ----------

    private String fotoJugador(String archivo) {
        if (supabaseUrl == null || supabaseUrl.isBlank()) return null;
        return supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/public/club-media/jugadores/" + archivo;
    }

    private static UUID jugadorId(int n) {
        return uuid("10000000", n);
    }

    private static UUID partidoId(int n) {
        return uuid("20000000", n);
    }

    private static UUID noticiaId(int n) {
        return uuid("30000000", n);
    }

    private static UUID uuid(String prefijo, int n) {
        return UUID.fromString(String.format("%s-0000-0000-0000-%012d", prefijo, n));
    }
}