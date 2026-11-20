package com.asee.entradas.controller;

import com.asee.entradas.config.SecurityConfig;
import com.asee.entradas.dto.AsientoDto;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccesoController.class)
@Import(SecurityConfig.class)
class AccesoControllerTest {

    @Autowired MockMvc mvc;
    @MockBean EntradasService service;
    @MockBean JwtDecoder jwtDecoder;

    private static EntradaDto usada() {
        return new EntradaDto("e1", "p1", "u1", new AsientoDto("a1", "A", 1, 1, EstadoAsiento.ocupado),
                TipoEntrada.general, 8.5, 0, MetodoPago.tarjeta, "QR-ABC", EstadoEntrada.usada,
                Instant.parse("2026-05-01T10:00:00Z"));
    }

    @Test
    void validar_staffMarcaLaEntradaComoUsada() throws Exception {
        when(service.validarEntrada("e1", true)).thenReturn(usada());

        mvc.perform(post("/entradas/e1/validacion").with(jwt().jwt(j -> j.subject("s1").claim("rol", "staff"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entradaId").value("e1"))
                .andExpect(jsonPath("$.estado").value("usada"));
    }

    @Test
    void validar_unSocio_pasaFalseYElServicioResponde403() throws Exception {
        when(service.validarEntrada("e1", false))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "Solo staff puede validar entradas"));

        mvc.perform(post("/entradas/e1/validacion").with(jwt().jwt(j -> j.subject("u1").claim("rol", "socio"))))
                .andExpect(status().isForbidden());

        verify(service).validarEntrada("e1", false);
    }

    @Test
    void validar_entradaYaUsada_devuelve409() throws Exception {
        when(service.validarEntrada("e1", true))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "La entrada ya fue usada o está anulada"));

        mvc.perform(post("/entradas/e1/validacion").with(jwt().jwt(j -> j.subject("s1").claim("rol", "staff"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(409));
    }

    @Test
    void validar_sinToken_devuelve401() throws Exception {
        mvc.perform(post("/entradas/e1/validacion")).andExpect(status().isUnauthorized());
    }
}
