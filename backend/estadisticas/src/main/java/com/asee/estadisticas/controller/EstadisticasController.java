package com.asee.estadisticas.controller;

import com.asee.estadisticas.dto.*;
import com.asee.estadisticas.model.*;
import com.asee.estadisticas.service.EstadisticasService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class EstadisticasController {
    private final EstadisticasService service;
    public EstadisticasController(EstadisticasService service) { this.service=service; }
    @GetMapping("/partidos/{partidoId}/estadisticas")
    public EstadisticasPartido partido(@PathVariable UUID partidoId) { return service.obtener(partidoId); }
    @PutMapping("/partidos/{partidoId}/estadisticas")
    public EstadisticasPartido corregir(@PathVariable UUID partidoId,@Valid @RequestBody EstadisticasPartidoUpdate body,@AuthenticationPrincipal Jwt jwt) {
        return service.corregir(partidoId,body,UUID.fromString(jwt.getSubject()));
    }
    @GetMapping("/partidos/{partidoId}/jugadores/{jugadorId}/estadisticas")
    public EstadisticaJugadorPartido jugadorPartido(@PathVariable UUID partidoId,@PathVariable UUID jugadorId) { return service.jugadorPartido(partidoId,jugadorId); }
    @PutMapping("/partidos/{partidoId}/jugadores/{jugadorId}/estadisticas")
    public EstadisticaJugadorPartido corregirJugador(@PathVariable UUID partidoId,@PathVariable UUID jugadorId,
            @Valid @RequestBody EstadisticaJugadorPartidoUpdate body,@AuthenticationPrincipal Jwt jwt) {
        return service.corregirJugador(partidoId,jugadorId,body,UUID.fromString(jwt.getSubject()));
    }
    @GetMapping("/partidos/{partidoId}/eventos")
    public List<EventoEstadistico> eventos(@PathVariable UUID partidoId,@RequestParam(required=false) Instant desde,
            @RequestParam(required=false) UUID jugadorId,@RequestParam(required=false) TipoEventoEstadistico tipo) {
        return service.eventos(partidoId,desde,jugadorId,tipo);
    }
    @PostMapping("/partidos/{partidoId}/eventos") @ResponseStatus(HttpStatus.CREATED)
    public EventoEstadistico registrar(@PathVariable UUID partidoId,@Valid @RequestBody EventoEstadisticoCreate body) { return service.registrar(partidoId,body); }
    @DeleteMapping("/partidos/{partidoId}/eventos/{eventoId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void anular(@PathVariable UUID partidoId,@PathVariable UUID eventoId) { service.anular(partidoId,eventoId); }
    @GetMapping("/jugadores/{jugadorId}/estadisticas")
    public EstadisticasJugadorTemporada jugadorTemporada(@PathVariable UUID jugadorId,
            @RequestParam @Pattern(regexp="^[0-9]{4}-[0-9]{4}$") String temporada,
            @RequestParam(required=false) String competicion,@RequestParam(defaultValue="false") boolean incluirPartidos) {
        return service.jugadorTemporada(jugadorId,temporada,competicion,incluirPartidos);
    }
    @GetMapping("/jugadores/{jugadorId}/partidos")
    public List<EstadisticaJugadorPartido> historial(@PathVariable UUID jugadorId,
            @RequestParam @Pattern(regexp="^[0-9]{4}-[0-9]{4}$") String temporada,@RequestParam(required=false) String competicion) {
        return service.historico(jugadorId,temporada,competicion);
    }
    @GetMapping("/temporadas/{temporada}/estadisticas")
    public List<EstadisticasJugadorTemporada> temporada(@PathVariable @Pattern(regexp="^[0-9]{4}-[0-9]{4}$") String temporada,
            @RequestParam(required=false) String competicion) { return service.temporada(temporada,competicion); }
    @GetMapping("/temporadas/{temporada}/lideres")
    public RankingEstadistico lideres(@PathVariable @Pattern(regexp="^[0-9]{4}-[0-9]{4}$") String temporada,
            @RequestParam MetricaRanking metrica,@RequestParam(required=false) String competicion,
            @RequestParam(defaultValue="1") @Min(0) int minimoPartidos,@RequestParam(defaultValue="10") @Min(1) @Max(50) int limite) {
        return service.lideres(temporada,competicion,metrica,minimoPartidos,limite);
    }
}
