package com.asee.deportivo.service;

import com.asee.deportivo.dto.NoticiaCreate;
import com.asee.deportivo.exception.RecursoNoEncontradoException;
import com.asee.deportivo.model.CategoriaNoticia;
import com.asee.deportivo.model.Noticia;
import com.asee.deportivo.repository.NoticiaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NoticiaService {

    private final NoticiaRepository repository;

    public NoticiaService(NoticiaRepository repository) {
        this.repository = repository;
    }

    public List<Noticia> listar(CategoriaNoticia categoria, UUID jugadorId, int pagina, int tamano) {
        // De más reciente a más antigua, como dice el YAML
        Pageable pageable = PageRequest.of(pagina - 1, tamano, Sort.by("fechaPublicacion").descending());

        if (categoria != null && jugadorId != null) {
            return repository.findByCategoriaAndJugadoresRelacionadosContaining(
                    categoria, jugadorId, pageable);
        }
        if (categoria != null) {
            return repository.findByCategoria(categoria, pageable);
        }
        if (jugadorId != null) {
            return repository.findByJugadoresRelacionadosContaining(jugadorId, pageable);
        }
        return repository.findAll(pageable).getContent();
    }

    public Noticia obtener(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe ninguna noticia con el identificador " + id));
    }

    public Noticia crear(NoticiaCreate dto, UUID autorId) {
        Noticia nueva = new Noticia(
                UUID.randomUUID(),
                dto.titulo(),
                dto.contenido(),
                dto.categoria(),
                dto.jugadoresRelacionados() != null ? dto.jugadoresRelacionados() : List.of(),
                dto.imagenUrl(),
                autorId,
                Instant.now());
        return repository.save(nueva);
    }

    public Noticia actualizar(UUID id, NoticiaCreate dto) {
        Noticia actual = obtener(id);
        // Se conservan el autor original y la fecha de publicación
        Noticia actualizada = new Noticia(
                id,
                dto.titulo(),
                dto.contenido(),
                dto.categoria(),
                dto.jugadoresRelacionados() != null ? dto.jugadoresRelacionados() : List.of(),
                dto.imagenUrl(),
                actual.autorId(),
                actual.fechaPublicacion());
        return repository.save(actualizada);
    }

    public void eliminar(UUID id) {
        if (!repository.existsById(id)) {
            throw new RecursoNoEncontradoException(
                    "No existe ninguna noticia con el identificador " + id);
        }
        repository.deleteById(id);
    }
}