package com.asee.entradas.controller;

import com.asee.entradas.config.SecurityConfig;
import com.asee.entradas.dto.AsientoDto;
import com.asee.entradas.dto.CompraEntradas;
import com.asee.entradas.dto.EntradaDto;
import com.asee.entradas.entity.EstadoAsiento;
import com.asee.entradas.entity.EstadoEntrada;
import com.asee.entradas.entity.MetodoPago;
import com.asee.entradas.entity.TipoEntrada;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

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

@WebMvcTest(EntradasController.class)
@Import(SecurityConfig.class)
class EntradasControllerTest {

    @Autowired MockMvc mvc;
    @MockBean EntradasService service;
    @MockBean JwtDecoder jwtDecoder;

    private static final String CUERPO_COMPRA =
            "{\"reservaId\":\"r1\",\"tipoEntrada\":\"general\",\"metodoPago\":\"tarjeta\"}";

    private static EntradaDto entradaDto() {
        return new EntradaDto("e1", "p1", "u1",
                new AsientoDto("a1", "A", 1, 1, EstadoAsiento.ocupado),
                TipoEntrada.general, 7.65, 10.0, MetodoPago.tarjeta, "QR-ABC",
                EstadoEntrada.valida, Instant.parse("2026-05-01T10:00:00Z"));
    }

    private static RequestPostProcessor usuario(String id, String rol) {
        return jwt().jwt(j -> j.subject(id).claim("rol", rol));
    }

    // ── POST /entradas ────────────────────────────

    @Test
    void comprar_devuelve201YUsaElUsuarioDelToken() throws Exception {
        when(service.comprarEntradas(new CompraEntradas("r1", TipoEntrada.general, MetodoPago.tarjeta), "u1"))
                .thenReturn(List.of(entradaDto()));

        mvc.perform(post("/entradas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_COMPRA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].entradaId").value("e1"))
                .andExpect(jsonPath("$[0].precioPagado").value(7.65))
                .andExpect(jsonPath("$[0].codigoQr").value("QR-ABC"))
                .andExpect(jsonPath("$[0].estado").value("valida"));
    }

    @Test
    void comprar_sinToken_devuelve401() throws Exception {
        mvc.perform(post("/entradas").contentType(MediaType.APPLICATION_JSON).content(CUERPO_COMPRA))
                .andExpect(status().isUnauthorized());

        verify(service, never()).comprarEntradas(any(), any());
    }

    @Test
    void comprar_conCamposObligatoriosAusentes_devuelve400() throws Exception {
        mvc.perform(post("/entradas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reservaId\":\"r1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Petición no válida"));
    }

    @Test
    void comprar_conTipoDeEntradaDesconocido_devuelve400() throws Exception {
        mvc.perform(post("/entradas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reservaId\":\"r1\",\"tipoEntrada\":\"xxx\",\"metodoPago\":\"tarjeta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Petición mal formada"));
    }

    @Test
    void comprar_reservaCaducada_seTraduceA410() throws Exception {
        when(service.comprarEntradas(any(CompraEntradas.class), any(String.class)))
                .thenThrow(new ApiException(HttpStatus.GONE, "La reserva ha caducado"));

        mvc.perform(post("/entradas").with(usuario("u1", "socio"))
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_COMPRA))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value(410));
    }

    // ── GET /entradas ─────────────────────────────

    @Test
    void listar_unSocioConsultaSusEntradas() throws Exception {
        when(service.listarEntradas("u1", null, "u1", false)).thenReturn(List.of(entradaDto()));

        mvc.perform(get("/entradas").param("usuarioId", "u1").with(usuario("u1", "socio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entradaId").value("e1"));
    }

    @Test
    void listar_conFiltroDePartidoYComoStaff() throws Exception {
        when(service.listarEntradas("u1", "p1", "s1", true)).thenReturn(List.of());

        mvc.perform(get("/entradas").param("usuarioId", "u1").param("partidoId", "p1")
                        .with(usuario("s1", "staff")))
                .andExpect(status().isOk());

        verify(service).listarEntradas("u1", "p1", "s1", true);
    }

    @Test
    void listar_sinUsuarioId_devuelve400() throws Exception {
        mvc.perform(get("/entradas").with(usuario("u1", "socio")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listar_sinToken_devuelve401() throws Exception {
        mvc.perform(get("/entradas").param("usuarioId", "u1")).andExpect(status().isUnauthorized());
    }

    @Test
    void listar_deOtroUsuario_devuelve403() throws Exception {
        when(service.listarEntradas("u1", null, "u2", false))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Solo puedes consultar tus propias entradas"));

        mvc.perform(get("/entradas").param("usuarioId", "u1").with(usuario("u2", "socio")))
                .andExpect(status().isForbidden());
    }

    // ── GET /entradas/{id} ────────────────────────

    @Test
    void obtener_devuelveLaEntradaConSuAsiento() throws Exception {
        when(service.obtenerEntrada("e1", "u1", false)).thenReturn(entradaDto());

        mvc.perform(get("/entradas/e1").with(usuario("u1", "socio")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asiento.zona").value("A"))
                .andExpect(jsonPath("$.metodoPago").value("tarjeta"));
    }

    @Test
    void obtener_inexistente_devuelve404() throws Exception {
        when(service.obtenerEntrada("zz", "u1", false))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Entrada no encontrada: zz"));

        mvc.perform(get("/entradas/zz").with(usuario("u1", "socio")))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /entradas/{id} ─────────────────────

    @Test
    void anular_staff_devuelve204() throws Exception {
        mvc.perform(delete("/entradas/e1").with(usuario("s1", "staff")))
                .andExpect(status().isNoContent());

        verify(service).anularEntrada("e1", true);
    }

    @Test
    void anular_adminTambienEsStaff() throws Exception {
        mvc.perform(delete("/entradas/e1").with(usuario("a1", "admin")))
                .andExpect(status().isNoContent());

        verify(service).anularEntrada("e1", true);
    }

    @Test
    void anular_noStaff_pasaFalseYElServicioResponde403() throws Exception {
        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.FORBIDDEN, "Solo staff/admin puede anular entradas"))
                .when(service).anularEntrada("e1", false);

        mvc.perform(delete("/entradas/e1").with(usuario("u1", "socio")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value(403));
    }

    @Test
    void anular_sinToken_devuelve401() throws Exception {
        mvc.perform(delete("/entradas/e1")).andExpect(status().isUnauthorized());
    }
}
