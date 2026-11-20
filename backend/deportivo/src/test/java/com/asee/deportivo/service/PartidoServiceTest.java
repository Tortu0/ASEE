package com.asee.deportivo.service;

import com.asee.deportivo.dto.PartidoCreate;
import com.asee.deportivo.exception.PeticionInvalidaException;
import com.asee.deportivo.exception.RecursoNoEncontradoException;
import com.asee.deportivo.model.EstadoPartido;
import com.asee.deportivo.model.Partido;
import com.asee.deportivo.repository.PartidoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartidoServiceTest {

    private static final Instant FECHA = Instant.parse("2026-11-15T18:30:00Z");

    @Mock
    private PartidoRepository repository;

    @InjectMocks
    private PartidoService service;

    private static PartidoCreate dto(String temporada) {
        return new PartidoCreate(FECHA, "CB Navalmoral", "Pabellón Municipal", true,
                temporada, "Liga regional", 7, null, null, null, null);
    }

    private static Partido existente(UUID id) {
        return new Partido(id, FECHA, "CB Navalmoral", "Pabellón Municipal", true,
                "2026-2027", "Liga regional", 7, EstadoPartido.finalizado, 78, 71, true);
    }

    @Test
    void crearAplicaValoresPorDefecto() {
        when(repository.save(any(Partido.class))).thenAnswer(i -> i.getArgument(0));

        Partido creado = service.crear(dto("2026-2027"));

        assertThat(creado.partidoId()).isNotNull();
        assertThat(creado.estado()).isEqualTo(EstadoPartido.programado);
        assertThat(creado.ventaEntradasAbierta()).isFalse();
    }

    @Test
    void actualizarConOtraTemporadaLanzaPeticionInvalida() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(existente(id)));

        assertThatThrownBy(() -> service.actualizar(id, dto("2027-2028")))
                .isInstanceOf(PeticionInvalidaException.class)
                .hasMessageContaining("temporada");
        verify(repository, never()).save(any(Partido.class));
    }

    @Test
    void actualizarConservaEstadoYVentaSiNoSeEnvian() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(existente(id)));
        when(repository.save(any(Partido.class))).thenAnswer(i -> i.getArgument(0));

        Partido actualizado = service.actualizar(id, dto("2026-2027"));

        assertThat(actualizado.estado()).isEqualTo(EstadoPartido.finalizado);
        assertThat(actualizado.ventaEntradasAbierta()).isTrue();
    }

    @Test
    void obtenerInexistenteLanzaNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(id))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eliminarInexistenteLanzaNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(id))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(repository, never()).deleteById(any(UUID.class));
    }
}