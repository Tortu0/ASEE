package com.asee.entradas.exception;

import com.asee.entradas.dto.ReservaCreate;
import com.asee.entradas.entity.EstadoAsiento;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Comprueba cómo {@link ApiExceptionHandler} traduce cada excepción a una respuesta JSON {@code ErrorDto}. */
class ApiExceptionHandlerTest {

    @RestController
    static class ControladorDePrueba {
        @GetMapping("/api")
        String api() { throw new ApiException(HttpStatus.CONFLICT, "choque de estado"); }

        @PostMapping("/valida")
        String valida(@Valid @RequestBody ReservaCreate body) { return "ok"; }

        @GetMapping("/numero")
        String numero(@RequestParam("n") Integer n) { return "ok"; }

        @GetMapping("/estado")
        String estado(@RequestParam("estado") EstadoAsiento estado) { return "ok"; }

        @GetMapping("/duplicado")
        String duplicado() { throw new DuplicateKeyException("E11000 duplicate key"); }

        @GetMapping("/boom")
        String boom() { throw new IllegalStateException("detalle interno secreto"); }
    }

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ControladorDePrueba())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void apiException_usaSuCodigoYMensaje() throws Exception {
        mvc.perform(get("/api"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(409))
                .andExpect(jsonPath("$.mensaje").value("choque de estado"));
    }

    @Test
    void cuerpoNoValido_devuelve400ConDetallesPorCampo() throws Exception {
        mvc.perform(post("/valida").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"partidoId\":\"\",\"asientos\":[\"a1\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje").value("Petición no válida"))
                .andExpect(jsonPath("$.detalles", hasItem(startsWith("partidoId:"))));
    }

    @Test
    void jsonMalFormado_devuelve400() throws Exception {
        mvc.perform(post("/valida").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Petición mal formada"));
    }

    @Test
    void parametroConTipoInvalido_devuelve400() throws Exception {
        mvc.perform(get("/numero").param("n", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Petición mal formada"));
    }

    @Test
    void enumDesconocidoEnParametro_devuelve400() throws Exception {
        mvc.perform(get("/estado").param("estado", "inventado"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void parametroObligatorioAusente_devuelve400() throws Exception {
        mvc.perform(get("/numero"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400));
    }

    @Test
    void claveDuplicada_devuelve409() throws Exception {
        mvc.perform(get("/duplicado"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value(409));
    }

    @Test
    void errorInesperado_devuelve500SinFiltrarDetallesInternos() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value(500))
                .andExpect(jsonPath("$.mensaje").value("Error interno del servidor"))
                .andExpect(content().string(not(containsString("secreto"))));
    }
}
