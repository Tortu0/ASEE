package com.asee.institucional.config;

import com.asee.institucional.model.CategoriaCantera;
import com.asee.institucional.model.Hito;
import com.asee.institucional.model.Historia;
import com.asee.institucional.model.Identidad;
import com.asee.institucional.model.Instalacion;
import com.asee.institucional.model.Junta;
import com.asee.institucional.model.MiembroJunta;
import com.asee.institucional.model.NivelPatrocinio;
import com.asee.institucional.model.Patrocinador;
import com.asee.institucional.model.Titulo;
import com.asee.institucional.repository.CategoriaCanteraRepository;
import com.asee.institucional.repository.HistoriaRepository;
import com.asee.institucional.repository.IdentidadRepository;
import com.asee.institucional.repository.InstalacionRepository;
import com.asee.institucional.repository.JuntaRepository;
import com.asee.institucional.repository.PatrocinadorRepository;
import com.asee.institucional.repository.TituloRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.datos-ejemplo", havingValue = "true")
public class DatosEjemploInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosEjemploInitializer.class);

    private final HistoriaRepository historia;
    private final IdentidadRepository identidad;
    private final JuntaRepository junta;
    private final InstalacionRepository instalaciones;
    private final TituloRepository palmares;
    private final CategoriaCanteraRepository cantera;
    private final PatrocinadorRepository patrocinadores;

    @Value("${SUPABASE_URL:}")
    private String supabaseUrl;

    public DatosEjemploInitializer(HistoriaRepository historia,
                                   IdentidadRepository identidad,
                                   JuntaRepository junta,
                                   InstalacionRepository instalaciones,
                                   TituloRepository palmares,
                                   CategoriaCanteraRepository cantera,
                                   PatrocinadorRepository patrocinadores) {
        this.historia = historia;
        this.identidad = identidad;
        this.junta = junta;
        this.instalaciones = instalaciones;
        this.palmares = palmares;
        this.cantera = cantera;
        this.patrocinadores = patrocinadores;
    }

    @Override
    public void run(String... args) {
        try {
            cargarHistoria();
            cargarIdentidad();
            cargarJunta();
            cargarInstalaciones();
            cargarPalmares();
            cargarCantera();
            cargarPatrocinadores();
        } catch (Exception e) {
            // Si MongoDB no está disponible, la aplicación sigue arrancando
            log.warn("No se han cargado los datos de ejemplo: {}", e.getMessage());
        }
    }

    private void cargarHistoria() {
        if (historia.existsById(Historia.ID_UNICO)) {
            log.info("Historia: ya hay datos, no se carga nada");
            return;
        }
        historia.save(new Historia(
                Historia.ID_UNICO,
                "El Club Baloncesto Madroñera nació en 1985 con una idea sencilla: acercar el baloncesto a todas las edades del pueblo. "
                        + "Lo que empezó con dos canastas en el patio del colegio es hoy una entidad con equipos desde premini hasta el primer equipo.\n"
                        + "A lo largo de estas décadas el club ha crecido de la mano de sus socios, sus familias y de los vecinos que han llenado el pabellón cada fin de semana. "
                        + "Hoy seguimos fieles al mismo espíritu: formar jugadores y, sobre todo, personas.",
                LocalDate.of(1985, 6, 1),
                List.of(
                        new Hito(1985, "Fundación del club"),
                        new Hito(1992, "Primera escuela de baloncesto para niños y niñas"),
                        new Hito(1998, "Primer ascenso de categoría"),
                        new Hito(2005, "Inauguración del pabellón municipal"),
                        new Hito(2016, "Ascenso de categoría tras una temporada histórica"),
                        new Hito(2019, "Campeones de la liga regional"),
                        new Hito(2023, "Copa Extremadura"))));
        log.info("Historia: cargada de ejemplo");
    }

    private void cargarIdentidad() {
        if (identidad.existsById(Identidad.ID_UNICO)) {
            log.info("Identidad: ya hay datos, no se carga nada");
            return;
        }
        identidad.save(new Identidad(
                Identidad.ID_UNICO,
                "CB Madroñera",
                null,
                List.of("#0B5FA5", "#FFFFFF")));
        log.info("Identidad: cargada de ejemplo");
    }

    private void cargarJunta() {
        if (junta.existsById(Junta.ID_UNICO)) {
            log.info("Junta: ya hay datos, no se carga nada");
            return;
        }
        junta.save(new Junta(Junta.ID_UNICO, List.of(
                new MiembroJunta("Ana García", "Presidenta", fotoClub("presidenta.webp", null)),
                new MiembroJunta("Manuel Rubio", "Vicepresidente", fotoClub("vicepresidente.webp", null)),
                new MiembroJunta("Lucía Herrera", "Secretaria", fotoClub("secretaria.webp", null)),
                new MiembroJunta("Marta Ruiz", "Tesorera", fotoClub("tesorera.webp", null)))));
        log.info("Junta: cargada de ejemplo");
    }

    private void cargarInstalaciones() {
        if (instalaciones.count() > 0) {
            log.info("Instalaciones: la colección ya tiene datos, no se carga nada");
            return;
        }
        instalaciones.saveAll(List.of(
                new Instalacion(instalacionId(1), "Pabellón Municipal de Madroñera",
                        "Calle del Deporte s/n, Madroñera (Cáceres)",
                        "Se accede desde la carretera principal; hay aparcamiento gratuito junto al pabellón y acceso adaptado para personas con movilidad reducida.",
                        600, List.of(fotoClub("pabellon.webp", "/img/club/pabellon.svg")), null, null),
                new Instalacion(instalacionId(2), "Pista exterior",
                        "Calle del Deporte s/n, Madroñera (Cáceres)",
                        "Junto al pabellón. De uso libre cuando no hay entrenamientos de la cantera.",
                        null, List.of(fotoClub("pista.webp", "/img/club/pista-exterior.svg")), null, null)));
        log.info("Instalaciones: cargadas 2 de ejemplo");
    }

    private void cargarPalmares() {
        if (palmares.count() > 0) {
            log.info("Palmarés: la colección ya tiene datos, no se carga nada");
            return;
        }
        palmares.saveAll(List.of(
                new Titulo(tituloId(1), "Ascenso de categoría", "2015-2016",
                        "Ascenso conseguido en la última jornada ante un pabellón lleno", null),
                new Titulo(tituloId(2), "Campeón de liga regional", "2018-2019",
                        "Liga ganada con el mejor balance de victorias de la historia del club", null),
                new Titulo(tituloId(3), "Copa Extremadura", "2022-2023",
                        "Final ganada en la prórroga", null)));
        log.info("Palmarés: cargados 3 títulos de ejemplo");
    }

    private void cargarCantera() {
        if (cantera.count() > 0) {
            log.info("Cantera: la colección ya tiene datos, no se carga nada");
            return;
        }
        cantera.saveAll(List.of(
                new CategoriaCantera(canteraId(1), "Premini", "8-9 años", "Lucía Herrera", "Lunes y miércoles 17:00"),
                new CategoriaCantera(canteraId(2), "Mini", "10-11 años", "Manuel Rubio", "Lunes y miércoles 18:00"),
                new CategoriaCantera(canteraId(3), "Infantil", "12-13 años", "Ana García", "Martes y jueves 17:00"),
                new CategoriaCantera(canteraId(4), "Cadete", "14-15 años", "Marta Ruiz", "Martes y jueves 18:00"),
                new CategoriaCantera(canteraId(5), "Junior", "16-17 años", "Marta Ruiz", "Martes y jueves 19:30")));
        log.info("Cantera: cargadas 5 categorías de ejemplo");
    }

    private void cargarPatrocinadores() {
        if (patrocinadores.count() > 0) {
            log.info("Patrocinadores: la colección ya tiene datos, no se carga nada");
            return;
        }
        patrocinadores.saveAll(List.of(
                new Patrocinador(patrocinadorId(1), "Banco Rural", fotoClub("banco_rural.webp", "/img/patrocinadores/banco-rural.svg"),
                        "https://www.extremadura.com", NivelPatrocinio.principal),
                new Patrocinador(patrocinadorId(2), "Ferretería López", fotoClub("ferreteria_lopez.webp", "/img/patrocinadores/ferreteria-lopez.svg"),
                        "https://www.turismoextremadura.com", NivelPatrocinio.oro),
                new Patrocinador(patrocinadorId(3), "Bar El Pabellón", fotoClub("bar_el_pabellon.webp", "/img/patrocinadores/bar_el_pabellon.svg"),
                        null, NivelPatrocinio.plata),
                new Patrocinador(patrocinadorId(4), "Talleres Ruiz", fotoClub("talleres_ruiz.webp", "/img/patrocinadores/talleres-ruiz.svg"),
                        null, NivelPatrocinio.colaborador)));
        log.info("Patrocinadores: cargados 4 de ejemplo");
    }

    // ---------- UUID fijos: iguales en todos los ordenadores ----------

    private String fotoClub(String archivo, String respaldo) {
        if (supabaseUrl == null || supabaseUrl.isBlank()) return respaldo;
        return supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/public/club-media/club/" + archivo;
    }

    private static UUID patrocinadorId(int n) {
        return uuid("50000000", n);
    }

    private static UUID tituloId(int n) {
        return uuid("60000000", n);
    }

    private static UUID canteraId(int n) {
        return uuid("70000000", n);
    }

    private static UUID instalacionId(int n) {
        return uuid("80000000", n);
    }

    private static UUID uuid(String prefijo, int n) {
        return UUID.fromString(String.format("%s-0000-0000-0000-%012d", prefijo, n));
    }
}