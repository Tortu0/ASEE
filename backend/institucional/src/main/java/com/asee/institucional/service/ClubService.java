package com.asee.institucional.service;

import com.asee.institucional.dto.HistoriaDto;
import com.asee.institucional.dto.IdentidadDto;
import com.asee.institucional.dto.MiembroJuntaDto;
import com.asee.institucional.exception.PeticionInvalidaException;
import com.asee.institucional.model.Hito;
import com.asee.institucional.model.Historia;
import com.asee.institucional.model.Identidad;
import com.asee.institucional.model.Junta;
import com.asee.institucional.model.MiembroJunta;
import com.asee.institucional.repository.HistoriaRepository;
import com.asee.institucional.repository.IdentidadRepository;
import com.asee.institucional.repository.JuntaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClubService {

    private final HistoriaRepository historiaRepository;
    private final IdentidadRepository identidadRepository;
    private final JuntaRepository juntaRepository;

    public ClubService(HistoriaRepository historiaRepository,
                       IdentidadRepository identidadRepository,
                       JuntaRepository juntaRepository) {
        this.historiaRepository = historiaRepository;
        this.identidadRepository = identidadRepository;
        this.juntaRepository = juntaRepository;
    }

    // ---------- Historia ----------

    public Historia obtenerHistoria() {
        // El YAML no declara 404: si todavía no hay nada guardado, se devuelve vacío
        return historiaRepository.findById(Historia.ID_UNICO)
                .orElseGet(() -> new Historia(Historia.ID_UNICO, "", null, List.of()));
    }

    public Historia actualizarHistoria(HistoriaDto dto) {
        List<Hito> hitos = dto.hitos() == null
                ? List.of()
                : dto.hitos().stream()
                .map(h -> new Hito(h.anio(), h.descripcion()))
                .toList();
        return historiaRepository.save(
                new Historia(Historia.ID_UNICO, dto.texto(), dto.fechaFundacion(), hitos));
    }

    // ---------- Identidad ----------

    public Identidad obtenerIdentidad() {
        return identidadRepository.findById(Identidad.ID_UNICO)
                .orElseGet(() -> new Identidad(Identidad.ID_UNICO, "", null, List.of()));
    }

    public Identidad actualizarIdentidad(IdentidadDto dto) {
        return identidadRepository.save(new Identidad(
                Identidad.ID_UNICO,
                dto.nombreOficial(),
                dto.escudoUrl(),
                dto.coloresOficiales()));
    }

    // ---------- Junta directiva ----------

    public List<MiembroJunta> obtenerJunta() {
        return juntaRepository.findById(Junta.ID_UNICO)
                .map(Junta::miembros)
                .orElse(List.of());
    }

    public List<MiembroJunta> actualizarJunta(List<MiembroJuntaDto> dtos) {
        // Validación manual: Spring no valida de forma fiable los elementos
        // de una lista que llega como cuerpo de la petición
        List<MiembroJunta> miembros = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            MiembroJuntaDto m = dtos.get(i);
            if (m == null || vacio(m.nombre()) || vacio(m.cargo())) {
                throw new PeticionInvalidaException(
                        "miembros[" + i + "]: nombre y cargo son obligatorios");
            }
            miembros.add(new MiembroJunta(m.nombre(), m.cargo(), m.fotoUrl()));
        }
        // Un único save sustituye la composición entera, como pide el YAML
        juntaRepository.save(new Junta(Junta.ID_UNICO, miembros));
        return miembros;
    }

    private boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}