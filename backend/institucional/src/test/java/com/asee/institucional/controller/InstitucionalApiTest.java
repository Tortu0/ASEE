package com.asee.institucional.controller;

import com.asee.institucional.TestTokens;
import com.asee.institucional.model.CategoriaCantera;
import com.asee.institucional.model.Historia;
import com.asee.institucional.model.Identidad;
import com.asee.institucional.model.Instalacion;
import com.asee.institucional.model.Junta;
import com.asee.institucional.model.Patrocinador;
import com.asee.institucional.model.Titulo;
import com.asee.institucional.repository.CategoriaCanteraRepository;
import com.asee.institucional.repository.HistoriaRepository;
import com.asee.institucional.repository.IdentidadRepository;
import com.asee.institucional.repository.InstalacionRepository;
import com.asee.institucional.repository.JuntaRepository;
import com.asee.institucional.repository.PatrocinadorRepository;
import com.asee.institucional.repository.TituloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Seguridad real y repositorios sustituidos por dobles: no necesita MongoDB.
// MockMvc no aplica el context-path /v1, así que las rutas van sin él.
@SpringBootTest(properties = {"app.datos-ejemplo=false", "jwt.secret=" + TestTokens.SECRETO})
@AutoConfigureMockMvc
class InstitucionalApiTest {

    private static final String YO = "11111111-1111-1111-1111-111111111111";
    private static final String ID = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private HistoriaRepository historia;
    @MockitoBean
    private IdentidadRepository identidad;
    @MockitoBean
    private JuntaRepository junta;
    @MockitoBean
    private InstalacionRepository instalaciones;
    @MockitoBean
    private TituloRepository palmares;
    @MockitoBean
    private CategoriaCanteraRepository cantera;
    @MockitoBean
    private PatrocinadorRepository patrocinadores;

    @BeforeEach
    void guardarDevuelveLoGuardado() {
        when(historia.save(any(Historia.class))).thenAnswer(i -> i.getArgument(0));
        when(identidad.save(any(Identidad.class))).thenAnswer(i -> i.getArgument(0));
        when(junta.save(any(Junta.class))).thenAnswer(i -> i.getArgument(0));
        when(instalaciones.save(any(Instalacion.class))).thenAnswer(i -> i.getArgument(0));
        when(palmares.save(any(Titulo.class))).thenAnswer(i -> i.getArgument(0));
        when(cantera.save(any(CategoriaCantera.class))).thenAnswer(i -> i.getArgument(0));
        when(patrocinadores.save(any(Patrocinador.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static String como(String rol) {
        return TestTokens.bearer(YO, rol);
    }

    @Test
    void historiaEsPublicaYVaciaSiNoHayDatos() throws Exception {
        mvc.perform(get("/club/historia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value(""));
    }

    @Test
    void editarHistoriaSinTokenDa401() throws Exception {
        mvc.perform(put("/club/historia").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editarHistoriaComoVisitanteDa403() throws Exception {
        mvc.perform(put("/club/historia").header("Authorization", como("visitante"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarHistoriaComoStaffDa200() throws Exception {
        mvc.perform(put("/club/historia").header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"El club se fundo en 1985\",\"hitos\":[{\"anio\":1990,\"descripcion\":\"Ascenso\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("El club se fundo en 1985"));
    }

    @Test
    void editarIdentidadComoStaffDa403PorqueEsSoloAdmin() throws Exception {
        mvc.perform(put("/club/identidad").header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreOficial\":\"CB Madronera\",\"coloresOficiales\":[\"#0B5FA5\"]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarIdentidadComoAdminDa200() throws Exception {
        mvc.perform(put("/club/identidad").header("Authorization", como("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreOficial\":\"CB Madronera\",\"coloresOficiales\":[\"#0B5FA5\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreOficial").value("CB Madronera"));
    }

    @Test
    void identidadConColorInvalidoDa400() throws Exception {
        mvc.perform(put("/club/identidad").header("Authorization", como("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreOficial\":\"CB Madronera\",\"coloresOficiales\":[\"azul\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalles[0]").value(containsString("#RRGGBB")));
    }

    @Test
    void juntaConMiembroSinCargoDa400() throws Exception {
        mvc.perform(put("/club/junta").header("Authorization", como("admin"))
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"nombre\":\"Ana\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("miembros[0]")));
    }

    @Test
    void crearPatrocinadorComoStaffDa403PorqueEsSoloAdmin() throws Exception {
        mvc.perform(post("/patrocinadores").header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ferreteria Lopez\",\"nivel\":\"oro\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearPatrocinadorComoAdminDa201() throws Exception {
        mvc.perform(post("/patrocinadores").header("Authorization", como("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ferreteria Lopez\",\"nivel\":\"oro\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nivel").value("oro"));
    }

    @Test
    void patrocinadorInexistenteDa404() throws Exception {
        mvc.perform(get("/patrocinadores/" + ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404));
    }

    @Test
    void filtroDeNivelInvalidoDa400() throws Exception {
        mvc.perform(get("/patrocinadores").param("nivel", "diamante"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void borrarTituloComoStaffDa403PorqueEsSoloAdmin() throws Exception {
        mvc.perform(delete("/club/palmares/" + ID).header("Authorization", como("staff")))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearInstalacionComoStaffDa201() throws Exception {
        mvc.perform(post("/club/instalaciones").header("Authorization", como("staff"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Pabellon\",\"direccion\":\"Calle Mayor 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.instalacionId").exists());
    }
}