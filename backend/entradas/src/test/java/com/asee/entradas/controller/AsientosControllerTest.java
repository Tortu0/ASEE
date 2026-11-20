package com.asee.entradas.controller;

import com.asee.entradas.config.SecurityConfig;
import com.asee.entradas.dto.AforoDto;
import com.asee.entradas.dto.AsientoDto;
import com.asee.entradas.dto.PrecioDto;
import com.asee.entradas.entity.EstadoAsiento;
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

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Endpoints públicos (sin JWT): mapa de asientos, aforo y precios. */
@WebMvcTest(AsientosController.class)
@Import(SecurityConfig.class)
class AsientosControllerTest {

    @Autowired MockMvc mvc;
    @MockBean EntradasService service;
    @MockBean JwtDecoder jwtDecoder;   // requerido por oauth2ResourceServer().jwt()

    @Test
    void listarAsientos_esPublicoYSinFiltros() throws Exception {
        when(service.listarAsientos("p1", null, null))
                .thenReturn(List.of(new AsientoDto("a1", "A", 1, 2, EstadoAsiento.libre)));

        mvc.perform(get("/partidos/p1/asientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].asientoId").value("a1"))
                .andExpect(jsonPath("$[0].zona").value("A"))
                .andExpect(jsonPath("$[0].estado").value("libre"));
    }

    @Test
    void listarAsientos_pasaLosFiltrosAlServicio() throws Exception {
        when(service.listarAsientos("p1", "A", EstadoAsiento.reservado)).thenReturn(List.of());

        mvc.perform(get("/partidos/p1/asientos").param("zona", "A").param("estado", "reservado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        verify(service).listarAsientos("p1", "A", EstadoAsiento.reservado);
    }

    @Test
    void listarAsientos_estadoInvalido_devuelve400() throws Exception {
        mvc.perform(get("/partidos/p1/asientos").param("estado", "inventado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    void listarAsientos_partidoInexistente_devuelve404ConErrorDto() throws Exception {
        when(service.listarAsientos("zz", null, null))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "No existe ningún partido"));

        mvc.perform(get("/partidos/zz/asientos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value(404))
                .andExpect(jsonPath("$.mensaje").value("No existe ningún partido"));
    }

    @Test
    void aforo_esPublico() throws Exception {
        when(service.obtenerAforo("p1")).thenReturn(new AforoDto("p1", 300, 20, 5, 275));

        mvc.perform(get("/partidos/p1/aforo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacidadTotal").value(300))
                .andExpect(jsonPath("$.ocupados").value(20))
                .andExpect(jsonPath("$.reservados").value(5))
                .andExpect(jsonPath("$.libres").value(275));
    }

    @Test
    void precios_esPublico() throws Exception {
        when(service.listarPrecios("p1"))
                .thenReturn(List.of(new PrecioDto("A", TipoEntrada.socio, 6.0)));

        mvc.perform(get("/partidos/p1/precios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zona").value("A"))
                .andExpect(jsonPath("$[0].tipoEntrada").value("socio"))
                .andExpect(jsonPath("$[0].precio").value(6.0));
    }
}
