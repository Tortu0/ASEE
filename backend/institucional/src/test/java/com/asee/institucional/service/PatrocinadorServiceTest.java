package com.asee.institucional.service;

import com.asee.institucional.dto.PatrocinadorCreate;
import com.asee.institucional.exception.RecursoNoEncontradoException;
import com.asee.institucional.model.NivelPatrocinio;
import com.asee.institucional.model.Patrocinador;
import com.asee.institucional.repository.PatrocinadorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatrocinadorServiceTest {

    @Mock
    private PatrocinadorRepository repository;

    @InjectMocks
    private PatrocinadorService service;

    private static Patrocinador patrocinador(UUID id, NivelPatrocinio nivel) {
        return new Patrocinador(id, "Ferretería López", null, null, nivel);
    }

    @Test
    void listarSinNivelDevuelveTodos() {
        Patrocinador p = patrocinador(UUID.randomUUID(), NivelPatrocinio.oro);
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(p));

        assertThat(service.listar(null)).containsExactly(p);
    }

    @Test
    void listarConNivelUsaElFiltro() {
        Patrocinador p = patrocinador(UUID.randomUUID(), NivelPatrocinio.oro);
        when(repository.findByNivel(eq(NivelPatrocinio.oro), any(Sort.class))).thenReturn(List.of(p));

        assertThat(service.listar(NivelPatrocinio.oro)).containsExactly(p);
    }

    @Test
    void crearGeneraElIdentificador() {
        when(repository.save(any(Patrocinador.class))).thenAnswer(i -> i.getArgument(0));

        Patrocinador creado = service.crear(
                new PatrocinadorCreate("Banco Rural", null, null, NivelPatrocinio.principal));

        assertThat(creado.patrocinadorId()).isNotNull();
        assertThat(creado.nivel()).isEqualTo(NivelPatrocinio.principal);
    }

    @Test
    void actualizarConservaElIdentificador() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(patrocinador(id, NivelPatrocinio.oro)));
        when(repository.save(any(Patrocinador.class))).thenAnswer(i -> i.getArgument(0));

        Patrocinador actualizado = service.actualizar(id,
                new PatrocinadorCreate("Banco Rural", null, null, NivelPatrocinio.principal));

        assertThat(actualizado.patrocinadorId()).isEqualTo(id);
        assertThat(actualizado.nivel()).isEqualTo(NivelPatrocinio.principal);
    }

    @Test
    void actualizarInexistenteLanzaNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizar(id,
                new PatrocinadorCreate("X", null, null, NivelPatrocinio.oro)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(repository, never()).save(any(Patrocinador.class));
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