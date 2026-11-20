package com.asee.deportivo.service;

import com.asee.deportivo.dto.JugadorCreate;
import com.asee.deportivo.exception.ConflictoException;
import com.asee.deportivo.exception.RecursoNoEncontradoException;
import com.asee.deportivo.model.Jugador;
import com.asee.deportivo.model.Posicion;
import com.asee.deportivo.repository.JugadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JugadorServiceTest {

    @Mock
    private JugadorRepository repository;

    @InjectMocks
    private JugadorService service;

    private static Jugador jugador(UUID id, int dorsal) {
        return new Jugador(id, "Carlos Gómez", dorsal, Posicion.base, 185, null, null, true);
    }

    private static JugadorCreate dto(int dorsal, Boolean activo) {
        return new JugadorCreate("Carlos Gómez", dorsal, Posicion.base, 190, null, null, activo);
    }

    @Test
    void crearConDorsalLibreGuardaJugadorActivo() {
        when(repository.findByDorsalAndActivoTrue(8)).thenReturn(Optional.empty());
        when(repository.save(any(Jugador.class))).thenAnswer(i -> i.getArgument(0));

        Jugador creado = service.crear(dto(8, null));

        assertThat(creado.jugadorId()).isNotNull();
        assertThat(creado.dorsal()).isEqualTo(8);
        assertThat(creado.activo()).isTrue();
    }

    @Test
    void crearConDorsalOcupadoLanzaConflicto() {
        when(repository.findByDorsalAndActivoTrue(8))
                .thenReturn(Optional.of(jugador(UUID.randomUUID(), 8)));

        assertThatThrownBy(() -> service.crear(dto(8, null)))
                .isInstanceOf(ConflictoException.class);
        verify(repository, never()).save(any(Jugador.class));
    }

    @Test
    void crearJugadorInactivoNoComprueba​ElDorsal() {
        when(repository.save(any(Jugador.class))).thenAnswer(i -> i.getArgument(0));

        service.crear(dto(8, false));

        verify(repository, never()).findByDorsalAndActivoTrue(anyInt());
    }

    @Test
    void actualizarSinCambiarDeDorsalNoEsConflicto() {
        UUID id = UUID.randomUUID();
        Jugador actual = jugador(id, 9);
        when(repository.findById(id)).thenReturn(Optional.of(actual));
        when(repository.findByDorsalAndActivoTrue(9)).thenReturn(Optional.of(actual));
        when(repository.save(any(Jugador.class))).thenAnswer(i -> i.getArgument(0));

        Jugador actualizado = service.actualizar(id, dto(9, null));

        assertThat(actualizado.alturaCm()).isEqualTo(190);
    }

    @Test
    void actualizarConDorsalDeOtroJugadorLanzaConflicto() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(jugador(id, 9)));
        when(repository.findByDorsalAndActivoTrue(8))
                .thenReturn(Optional.of(jugador(UUID.randomUUID(), 8)));

        assertThatThrownBy(() -> service.actualizar(id, dto(8, null)))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void eliminarInexistenteLanzaNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.eliminar(id))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}