package com.asee.institucional.service;

import com.asee.institucional.dto.TituloCreate;
import com.asee.institucional.exception.RecursoNoEncontradoException;
import com.asee.institucional.model.Titulo;
import com.asee.institucional.repository.TituloRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TituloServiceTest {

    @Mock
    private TituloRepository repository;

    @InjectMocks
    private TituloService service;

    @Test
    void crearGeneraElIdentificador() {
        when(repository.save(any(Titulo.class))).thenAnswer(i -> i.getArgument(0));

        Titulo creado = service.crear(new TituloCreate("Campeón liga regional", "2018-2019", null, null));

        assertThat(creado.tituloId()).isNotNull();
        assertThat(creado.temporada()).isEqualTo("2018-2019");
    }

    @Test
    void actualizarInexistenteLanzaNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.actualizar(id,
                new TituloCreate("Liga", "2018-2019", null, null)))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void eliminarExistenteBorraElTitulo() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);

        service.eliminar(id);

        verify(repository).deleteById(id);
    }
}