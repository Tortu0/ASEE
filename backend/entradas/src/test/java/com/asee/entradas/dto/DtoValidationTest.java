package com.asee.entradas.dto;

import com.asee.entradas.entity.MetodoPago;
import com.asee.entradas.entity.TipoEntrada;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Restricciones de Bean Validation de los DTO de entrada. */
class DtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static Set<String> campos(Set<? extends ConstraintViolation<?>> v) {
        return v.stream().map(x -> x.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void reservaCreate_valida() {
        assertThat(validator.validate(new ReservaCreate("p1", List.of("a1", "a2")))).isEmpty();
    }

    @Test
    void reservaCreate_aceptaExactamente10Asientos() {
        List<String> diez = IntStream.rangeClosed(1, 10).mapToObj(i -> "a" + i).toList();

        assertThat(validator.validate(new ReservaCreate("p1", diez))).isEmpty();
    }

    @Test
    void reservaCreate_rechazaMasDe10Asientos() {
        List<String> once = IntStream.rangeClosed(1, 11).mapToObj(i -> "a" + i).toList();

        assertThat(campos(validator.validate(new ReservaCreate("p1", once)))).containsExactly("asientos");
    }

    @Test
    void reservaCreate_rechazaListaVaciaONula() {
        assertThat(campos(validator.validate(new ReservaCreate("p1", Collections.emptyList())))).contains("asientos");
        assertThat(campos(validator.validate(new ReservaCreate("p1", null)))).contains("asientos");
    }

    @Test
    void reservaCreate_rechazaPartidoVacioONulo() {
        assertThat(campos(validator.validate(new ReservaCreate("", List.of("a1"))))).containsExactly("partidoId");
        assertThat(campos(validator.validate(new ReservaCreate(null, List.of("a1"))))).containsExactly("partidoId");
        assertThat(campos(validator.validate(new ReservaCreate("   ", List.of("a1"))))).containsExactly("partidoId");
    }

    @Test
    void compraEntradas_valida() {
        assertThat(validator.validate(new CompraEntradas("r1", TipoEntrada.vip, MetodoPago.bizum))).isEmpty();
    }

    @Test
    void compraEntradas_rechazaCamposObligatoriosAusentes() {
        assertThat(campos(validator.validate(new CompraEntradas(null, null, null))))
                .containsExactlyInAnyOrder("reservaId", "tipoEntrada", "metodoPago");
        assertThat(campos(validator.validate(new CompraEntradas(" ", TipoEntrada.general, MetodoPago.paypal))))
                .containsExactly("reservaId");
    }
}
