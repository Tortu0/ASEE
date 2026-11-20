package com.asee.deportivo.service;

import com.asee.deportivo.dto.NoticiaCreate;
import com.asee.deportivo.exception.RecursoNoEncontradoException;
import com.asee.deportivo.model.CategoriaNoticia;
import com.asee.deportivo.model.Noticia;
import com.asee.deportivo.repository.NoticiaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoticiaServiceTest {

    @Mock
    private NoticiaRepository repository;

    @InjectMocks
    private NoticiaService service;

    private static Noticia noticia(UUID id, UUID autor, Instant fecha) {
        return new Noticia(id, "Titulo", "Contenido", CategoriaNoticia.fichaje,
                List.of(), null, autor, fecha);
    }

    private static NoticiaCreate dto() {
        return new NoticiaCreate("Nuevo titulo", "Nuevo contenido", CategoriaNoticia.fichaje, null, null);
    }

    @Test
    void crearAsignaAutorFechaYLista() {
        UUID autor = UUID.randomUUID();
        when(repository.save(any(Noticia.class))).thenAnswer(i -> i.getArgument(0));

        Noticia creada = service.crear(dto(), autor);

        assertThat(creada.noticiaId()).isNotNull();
        assertThat(creada.autorId()).isEqualTo(autor);
        assertThat(creada.fechaPublicacion()).isNotNull();
        assertThat(creada.jugadoresRelacionados()).isEmpty();
    }

    @Test
    void listarPorCategoriaUsaElFiltro() {
        Noticia n = noticia(UUID.randomUUID(), null, Instant.now());
        when(repository.findByCategoria(eq(CategoriaNoticia.fichaje), any(Pageable.class)))
                .thenReturn(List.of(n));

        assertThat(service.listar(CategoriaNoticia.fichaje, null, 1, 20)).containsExactly(n);
    }

    @Test
    void listarSinFiltrosDevuelveTodas() {
        Noticia n = noticia(UUID.randomUUID(), null, Instant.now());
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(n)));

        assertThat(service.listar(null, null, 1, 20)).containsExactly(n);
    }

    @Test
    void actualizarConservaAutorYFechaOriginales() {
        UUID id = UUID.randomUUID();
        UUID autor = UUID.randomUUID();
        Instant fecha = Instant.parse("2026-10-01T10:00:00Z");
        when(repository.findById(id)).thenReturn(Optional.of(noticia(id, autor, fecha)));
        when(repository.save(any(Noticia.class))).thenAnswer(i -> i.getArgument(0));

        Noticia actualizada = service.actualizar(id, dto());

        assertThat(actualizada.titulo()).isEqualTo("Nuevo titulo");
        assertThat(actualizada.autorId()).isEqualTo(autor);
        assertThat(actualizada.fechaPublicacion()).isEqualTo(fecha);
    }

    @Test
    void obtenerInexistenteLanzaNoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(id))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}