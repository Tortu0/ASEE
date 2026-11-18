package com.asee.deportivo.repository;

import com.asee.deportivo.model.CategoriaNoticia;
import com.asee.deportivo.model.Noticia;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.UUID;

public interface NoticiaRepository extends MongoRepository<Noticia, UUID> {

    List<Noticia> findByCategoria(CategoriaNoticia categoria, Pageable pageable);

    List<Noticia> findByJugadoresRelacionadosContaining(UUID jugadorId, Pageable pageable);

    List<Noticia> findByCategoriaAndJugadoresRelacionadosContaining(
            CategoriaNoticia categoria, UUID jugadorId, Pageable pageable);
}