package com.asee.deportivo.controller;

import com.asee.deportivo.TestTokens;
import com.asee.deportivo.model.EstadoPartido;
import com.asee.deportivo.model.Jugador;
import com.asee.deportivo.model.Noticia;
import com.asee.deportivo.model.Partido;
import com.asee.deportivo.model.Posicion;
import com.asee.deportivo.model.PreferenciasNotificacion;
import com.asee.deportivo.repository.JugadorRepository;
import com.asee.deportivo.repository.NoticiaRepository;
import com.asee.deportivo.repository.PartidoRepository;
import com.asee.deportivo.repository.PreferenciasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Arranca la aplicación con la seguridad real y los repositorios sustituidos por dobles:
// no necesita MongoDB. MockMvc no aplica el context-path /v1, así que las rutas van sin él.
@SpringBootTest(properties = {"app.datos-ejemplo=false", "jwt.secret=" + TestTokens.SECRETO})
@AutoConfigureMockMvc
class DeportivoApiTest {

    private static final String YO = "11111111-1111-1111-1111-111111111111";
    private static final String OTRO = "22222222-2222-2222-2222-222222222222";
    private static final String ID = "33333333-3333-3333-3333-333333333333";

    private static final String PARTIDO_VALIDO = """
            {"fechaHora":"2026-11-15T18:30:00Z","rival":"CB Navalmoral",
             "lugar":"Pabellon Municipal","temporada":"2026-2027"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PartidoRepository partidos;
    @MockitoBean
    private JugadorRepository jugadores;
    @MockitoBean
    private NoticiaRepository noticias;
    @MockitoBean
    private PreferenciasRepository preferencias;

    @BeforeEach
    void guardarDevuelveLoGuardado() {
        when(partidos.save(any(Partido.class))).thenAnswer(i -> i.getArgument(0));
        when(jugadores.save(any(Jugador.class))).thenAnswer(i -> i.getArgument(0));
        when(noticias.save(any(Noticia.class))).thenAnswer(i -> i.getArgument(0));
        when(preferencias.save(any(PreferenciasNotificacion.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static String como(String rol) {
        return TestTokens.bearer(YO, rol);
    }

    private static Jugador jugador(String id, int dorsal) {
        return new Jugador(UUID.fromString(id), "Carlos Gomez", dorsal, Posicion.base, 185, null, null, true);
    }

    @Test
    void listarPartidosEsPublico() throws Exception {
        mvc.perform(get("/partidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void crearPartidoSinTokenDa401() throws Exception {
        mvc.perform(post("/partidos").contentType(MediaType.APPLICATION_JSON).content(PARTIDO_VALIDO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value(401));
    }

    @Test
    void crearPartidoComoVisitanteDa403() throws Exception {
        mvc.perform(post("/partidos").header("Authorization", como("visitante"))
                        .contentType(MediaType.APPLICATION_JSON).content(PARTIDO_VALIDO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    void crearPartidoComoStaffDa201() throws Exception {
        mvc.perform(post("/partidos").header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON).content(PARTIDO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.estado").value("programado"))
                .andExpect(jsonPath("$.ventaEntradasAbierta").value(false));
        verify(partidos).save(any(Partido.class));
    }

    @Test
    void crearPartidoInvalidoDa400ConDetalles() throws Exception {
        mvc.perform(post("/partidos").header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rival\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles").isNotEmpty());
    }

    @Test
    void borrarPartidoComoStaffDa403() throws Exception {
        mvc.perform(delete("/partidos/" + ID).header("Authorization", como("staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    void borrarPartidoComoAdminDa204() throws Exception {
        when(partidos.existsById(UUID.fromString(ID))).thenReturn(true);

        mvc.perform(delete("/partidos/" + ID).header("Authorization", como("admin")))
                .andExpect(status().isNoContent());
    }

    @Test
    void partidoInexistenteDa404() throws Exception {
        mvc.perform(get("/partidos/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404));
    }

    @Test
    void cambiarLaTemporadaDa400() throws Exception {
        UUID id = UUID.fromString(ID);
        when(partidos.findById(id)).thenReturn(Optional.of(new Partido(id,
                Instant.parse("2026-11-15T18:30:00Z"), "CB Navalmoral", "Pabellon Municipal", true,
                "2026-2027", "Liga regional", 7, EstadoPartido.programado, null, null, false)));

        mvc.perform(put("/partidos/" + ID).header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PARTIDO_VALIDO.replace("2026-2027", "2027-2028")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("temporada")));
    }

    @Test
    void modificarJugadorComoStaffDa200() throws Exception {
        when(jugadores.findById(UUID.fromString(ID))).thenReturn(Optional.of(jugador(ID, 4)));

        mvc.perform(put("/jugadores/" + ID).header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Carlos Gomez Ruiz\",\"dorsal\":4,\"posicion\":\"base\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Carlos Gomez Ruiz"));
    }

    @Test
    void dorsalRepetidoDa409() throws Exception {
        when(jugadores.findByDorsalAndActivoTrue(4)).thenReturn(Optional.of(jugador(OTRO, 4)));

        mvc.perform(post("/jugadores").header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Luis Perez\",\"dorsal\":4,\"posicion\":\"alero\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(409));
    }

    @Test
    void crearNoticiaTomaElAutorDelToken() throws Exception {
        mvc.perform(post("/noticias").header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Nuevo base\",\"contenido\":\"El club ficha a un base.\",\"categoria\":\"fichaje\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autorId").value(YO));
    }

    @Test
    void preferenciasDeOtroUsuarioDa403() throws Exception {
        mvc.perform(get("/usuarios/" + OTRO + "/preferencias-notificacion")
                        .header("Authorization", como("visitante")))
                .andExpect(status().isForbidden());
    }

    @Test
    void preferenciasPropiasSinConfigurarDa404() throws Exception {
        mvc.perform(get("/usuarios/" + YO + "/preferencias-notificacion")
                        .header("Authorization", como("visitante")))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenFirmadoConOtraClaveDa401() throws Exception {
        String falso = "Bearer " + TestTokens.token(YO, "admin",
                "otra-clave-distinta-de-32-caracteres!!", 3600);

        mvc.perform(delete("/partidos/" + ID).header("Authorization", falso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenCaducadoDa401() throws Exception {
        String caducado = "Bearer " + TestTokens.token(YO, "admin", TestTokens.SECRETO, -3600);

        mvc.perform(delete("/partidos/" + ID).header("Authorization", caducado))
                .andExpect(status().isUnauthorized());
    }
}