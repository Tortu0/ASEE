package com.asee.entradas.controller;

import com.asee.entradas.config.SecurityConfig;
import com.asee.entradas.dto.AsientoDto;
import com.asee.entradas.dto.ReservaCreate;
import com.asee.entradas.dto.ReservaDto;
import com.asee.entradas.entity.EstadoAsiento;
import com.asee.entradas.exception.ApiException;
import com.asee.entradas.service.EntradasService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReservasController.class)
@Import(SecurityConfig.class)
class ReservasControllerTest {

    @Autowired MockMvc mvc;
    @MockBean EntradasService service;
    @MockBean JwtDecoder jwtDecoder;

    private static final Instant EXPIRA = Instant.parse("2030-01-01T10:00:00Z");

    private static ReservaDto reservaDto() {
        return new ReservaDto("r1", "p1", "u1",
                List.of(new AsientoDto("a1", "A", 1, 1, EstadoAsiento.reservado)), EXPIRA);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor usuario(String id, String rol) {
        return jwt().jwt(j -> j.subject(id).claim("rol", rol));
    }

    // ── POST /reservas ────────────────────────────

    @Test
    void crearReserva_devuelve201YUsaElUsuarioDelToken() throws Exception {
        when(service.crearReserva(new ReservaCreate("p1", List.of("a1")), "u1")).thenReturn(reservaDto());

        mvc.perform(post("/reservas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidoId\":\"p1\",\"asientos\":[\"a1\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservaId").value("r1"))
                .andExpect(jsonPath("$.usuarioId").value("u1"))
                .andExpect(jsonPath("$.asientos[0].estado").value("reservado"));
    }

    @Test
    void crearReserva_sinToken_devuelve401() throws Exception {
        mvc.perform(post("/reservas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidoId\":\"p1\",\"asientos\":[\"a1\"]}"))
                .andExpect(status().isUnauthorized());

        verify(service, never()).crearReserva(any(), any());
    }

    @Test
    void crearReserva_sinAsientos_devuelve400() throws Exception {
        mvc.perform(post("/reservas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidoId\":\"p1\",\"asientos\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Petición no válida"));

        verify(service, never()).crearReserva(any(), any());
    }

    @Test
    void crearReserva_masDe10Asientos_devuelve400() throws Exception {
        mvc.perform(post("/reservas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidoId\":\"p1\",\"asientos\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"11\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearReserva_sinPartido_devuelve400() throws Exception {
        mvc.perform(post("/reservas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asientos\":[\"a1\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearReserva_conflictoDelServicio_seTraduceA409() throws Exception {
        when(service.crearReserva(any(ReservaCreate.class), any(String.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "El asiento a1 no está libre"));

        mvc.perform(post("/reservas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidoId\":\"p1\",\"asientos\":[\"a1\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(409))
                .andExpect(jsonPath("$.mensaje").value("El asiento a1 no está libre"));
    }

    // ── GET /reservas/{id} ────────────────────────

    @Test
    void obtenerReserva_pasaElUsuarioYSiEsStaff() throws Exception {
        when(service.obtenerReserva("r1", "u1", false)).thenReturn(reservaDto());
        when(service.obtenerReserva("r1", "s1", true)).thenReturn(reservaDto());

        mvc.perform(get("/reservas/r1").with(usuario("u1", "socio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservaId").value("r1"));
        mvc.perform(get("/reservas/r1").with(usuario("s1", "staff")))
                .andExpect(status().isOk());

        verify(service).obtenerReserva("r1", "u1", false);
        verify(service).obtenerReserva("r1", "s1", true);
    }

    @Test
    void obtenerReserva_sinToken_devuelve401() throws Exception {
        mvc.perform(get("/reservas/r1")).andExpect(status().isUnauthorized());
    }

    @Test
    void obtenerReserva_ajena_devuelve403() throws Exception {
        when(service.obtenerReserva("r1", "u2", false))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "No tienes permiso"));

        mvc.perform(get("/reservas/r1").with(usuario("u2", "socio")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403));
    }

    // ── DELETE /reservas/{id} ─────────────────────

    @Test
    void cancelarReserva_devuelve204() throws Exception {
        mvc.perform(delete("/reservas/r1").with(usuario("u1", "socio")))
                .andExpect(status().isNoContent());

        verify(service).cancelarReserva("r1", "u1", false);
    }

    @Test
    void cancelarReserva_sinToken_devuelve401() throws Exception {
        mvc.perform(delete("/reservas/r1")).andExpect(status().isUnauthorized());
    }
}
