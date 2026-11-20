package com.asee.institucional.service;

import com.asee.institucional.dto.HistoriaDto;
import com.asee.institucional.dto.HitoDto;
import com.asee.institucional.dto.MiembroJuntaDto;
import com.asee.institucional.exception.PeticionInvalidaException;
import com.asee.institucional.model.Historia;
import com.asee.institucional.model.Identidad;
import com.asee.institucional.model.Junta;
import com.asee.institucional.model.MiembroJunta;
import com.asee.institucional.repository.HistoriaRepository;
import com.asee.institucional.repository.IdentidadRepository;
import com.asee.institucional.repository.JuntaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    @Mock
    private HistoriaRepository historia;
    @Mock
    private IdentidadRepository identidad;
    @Mock
    private JuntaRepository junta;

    @InjectMocks
    private ClubService service;

    @Test
    void historiaVaciaSiNoHayDatosGuardados() {
        when(historia.findById(Historia.ID_UNICO)).thenReturn(Optional.empty());

        Historia h = service.obtenerHistoria();

        assertThat(h.texto()).isEmpty();
        assertThat(h.hitos()).isEmpty();
    }

    @Test
    void actualizarHistoriaGuardaEnElDocumentoUnico() {
        when(historia.save(any(Historia.class))).thenAnswer(i -> i.getArgument(0));
        HistoriaDto dto = new HistoriaDto("Fundado en 1985", LocalDate.of(1985, 6, 1),
                List.of(new HitoDto(1990, "Primer ascenso")));

        Historia guardada = service.actualizarHistoria(dto);

        assertThat(guardada.id()).isEqualTo(Historia.ID_UNICO);
        assertThat(guardada.hitos()).hasSize(1);
    }

    @Test
    void identidadVaciaSiNoHayDatosGuardados() {
        when(identidad.findById(Identidad.ID_UNICO)).thenReturn(Optional.empty());

        Identidad i = service.obtenerIdentidad();

        assertThat(i.nombreOficial()).isEmpty();
        assertThat(i.coloresOficiales()).isEmpty();
    }

    @Test
    void juntaVaciaSiNoHayDatosGuardados() {
        when(junta.findById(Junta.ID_UNICO)).thenReturn(Optional.empty());

        assertThat(service.obtenerJunta()).isEmpty();
    }

    @Test
    void actualizarJuntaRechazaMiembroSinCargo() {
        List<MiembroJuntaDto> miembros = List.of(
                new MiembroJuntaDto("Ana", "Presidenta", null),
                new MiembroJuntaDto("Luis", null, null));

        assertThatThrownBy(() -> service.actualizarJunta(miembros))
                .isInstanceOf(PeticionInvalidaException.class)
                .hasMessageContaining("miembros[1]");
        verify(junta, never()).save(any(Junta.class));
    }

    @Test
    void actualizarJuntaSustituyeLaComposicion() {
        when(junta.save(any(Junta.class))).thenAnswer(i -> i.getArgument(0));

        List<MiembroJunta> resultado = service.actualizarJunta(List.of(
                new MiembroJuntaDto("Ana", "Presidenta", null),
                new MiembroJuntaDto("Luis", "Secretario", null)));

        assertThat(resultado).hasSize(2);
        verify(junta).save(any(Junta.class));
    }
}