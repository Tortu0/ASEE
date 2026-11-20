package com.asee.deportivo.service;

import com.asee.deportivo.dto.PreferenciasDto;
import com.asee.deportivo.exception.PeticionInvalidaException;
import com.asee.deportivo.exception.RecursoNoEncontradoException;
import com.asee.deportivo.model.PreferenciasNotificacion;
import com.asee.deportivo.repository.PreferenciasRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PreferenciasServiceTest {

    @Mock
    private PreferenciasRepository repository;

    @InjectMocks
    private PreferenciasService service;

    @Test
    void obtenerSinPreferenciasLanzaNoEncontrado() {
        UUID usuario = UUID.randomUUID();
        when(repository.findById(usuario)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(usuario))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizarConUsuarioDistintoEnElCuerpoLanzaPeticionInvalida() {
        UUID usuario = UUID.randomUUID();
        PreferenciasDto dto = new PreferenciasDto(UUID.randomUUID(), null, null, null);

        assertThatThrownBy(() -> service.actualizar(usuario, dto))
                .isInstanceOf(PeticionInvalidaException.class);
    }

    @Test
    void actualizarAplicaValoresPorDefecto() {
        UUID usuario = UUID.randomUUID();
        when(repository.save(any(PreferenciasNotificacion.class))).thenAnswer(i -> i.getArgument(0));

        PreferenciasNotificacion guardadas =
                service.actualizar(usuario, new PreferenciasDto(null, null, null, null));

        assertThat(guardadas.usuarioId()).isEqualTo(usuario);
        assertThat(guardadas.jugadoresFavoritos()).isEmpty();
        assertThat(guardadas.categoriasFavoritas()).isEmpty();
        assertThat(guardadas.avisarInicioPartido()).isTrue();
    }
}