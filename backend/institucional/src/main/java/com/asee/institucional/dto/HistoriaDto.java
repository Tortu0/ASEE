package com.asee.institucional.dto;

import com.asee.institucional.model.Historia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.List;

public record HistoriaDto(
        @NotBlank String texto,
        LocalDate fechaFundacion,
        List<@Valid HitoDto> hitos
) {
    public static HistoriaDto from(Historia h) {
        List<HitoDto> hitos = h.hitos() == null
                ? List.of()
                : h.hitos().stream().map(HitoDto::from).toList();
        return new HistoriaDto(h.texto(), h.fechaFundacion(), hitos);
    }
}