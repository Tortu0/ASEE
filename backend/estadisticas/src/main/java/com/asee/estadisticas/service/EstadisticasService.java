package com.asee.estadisticas.service;

import com.asee.estadisticas.dto.*;
import com.asee.estadisticas.model.*;
import com.asee.estadisticas.repository.*;
import com.asee.estadisticas.exception.ApiException;
import com.mongodb.MongoException;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class EstadisticasService {
    private final PartidoRepository partidos;
    private final EventoRepository eventos;
    private final AjusteRepository ajustes;
    private final DeportivoClient deportivo;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher publisher;
    public EstadisticasService(PartidoRepository partidos, EventoRepository eventos, AjusteRepository ajustes,
            DeportivoClient deportivo, TransactionTemplate tx, ApplicationEventPublisher publisher) {
        this.partidos=partidos; this.eventos=eventos; this.ajustes=ajustes; this.deportivo=deportivo; this.tx=tx; this.publisher=publisher;
    }
    public Optional<EstadisticasPartidoDocumento> persistido(UUID id) { return partidos.findById(id); }
    public EstadisticasPartidoDocumento estado(UUID id) { return partidos.findById(id).orElseGet(() -> nuevo(deportivo.partido(id))); }
    public EstadisticasPartido obtener(UUID id) { return Calculos.partido(estado(id)); }
    public EstadisticaJugadorPartido jugadorPartido(UUID partido, UUID jugador) {
        return obtener(partido).jugadores().stream().filter(j -> j.jugadorId().equals(jugador)).findFirst()
                .orElseThrow(() -> ApiException.missing("No hay estadisticas de ese jugador en el partido"));
    }
    public List<EventoEstadistico> eventos(UUID id, Instant desde, UUID jugador, TipoEventoEstadistico tipo) {
        estado(id);
        return eventos.findByPartidoIdOrderByRegistradoEnAsc(id).stream()
                .filter(e -> desde == null || e.registradoEn().isAfter(desde))
                .filter(e -> jugador == null || jugador.equals(e.jugadorId()))
                .filter(e -> tipo == null || e.tipo() == tipo).map(EstadisticasService::eventoDto).toList();
    }
    public EstadisticasPartido corregir(UUID id, EstadisticasPartidoUpdate dto, UUID autor) {
        var referencia = deportivo.partido(id);
        dto.getJugadores().forEach(j -> deportivo.jugador(j.jugadorId()));
        var guardado = escribir(id, () -> {
            var actual = partidos.findById(id).orElseGet(() -> nuevo(referencia));
            Map<UUID,EstadoEstadisticoJugador> jugadores = mapa(actual);
            for (var entrada : dto.getJugadores()) {
                var anterior = jugadores.get(entrada.jugadorId());
                var posterior = Calculos.corregir(anterior == null ? Calculos.vacio(entrada.jugadorId()) : anterior, entrada.estadisticas());
                jugadores.put(entrada.jugadorId(), posterior);
                ajustes.insert(new AjusteEstadisticoDocumento(UUID.randomUUID(),id,entrada.jugadorId(),autor,Instant.now(),
                        actual.versionPublica()+1,Calculos.campos(entrada.estadisticas()),anterior,posterior));
            }
            return partidos.save(copia(actual,actual.estadoSeguimiento(),
                    dto.tienePeriodoActual() ? dto.getPeriodoActual() : actual.periodoActual(),
                    dto.tieneSegundosRestantesPeriodo() ? dto.getSegundosRestantesPeriodo() : actual.segundosRestantesPeriodo(),jugadores));
        });
        return Calculos.partido(guardado);
    }
    public EstadisticaJugadorPartido corregirJugador(UUID id, UUID jugador, EstadisticaJugadorPartidoUpdate dto, UUID autor) {
        var partido = new EstadisticasPartidoUpdate();
        partido.setJugadores(List.of(new JugadorEstadisticasUpdate(jugador,dto)));
        return corregir(id,partido,autor).jugadores().stream().filter(j -> jugador.equals(j.jugadorId())).findFirst().orElseThrow();
    }
    public EventoEstadistico registrar(UUID id, EventoEstadisticoCreate dto) {
        var referencia = deportivo.partido(id);
        if (dto.getJugadorId()!=null) deportivo.jugador(dto.getJugadorId());
        if (dto.getJugadorRelacionadoId()!=null) deportivo.jugador(dto.getJugadorRelacionadoId());
        return escribir(id, () -> {
            if (eventos.existsByPartidoIdAndClientEventoId(id,dto.getClientEventoId())) throw new ApiException(409,"clientEventoId ya registrado en este partido");
            var actual = partidos.findById(id).orElseGet(() -> nuevo(referencia));
            long millis = eventos.findFirstByPartidoIdOrderByRegistradoEnDesc(id)
                    .map(e -> Math.max(System.currentTimeMillis(),e.registradoEn().toEpochMilli()+1)).orElse(System.currentTimeMillis());
            var evento = new EventoEstadisticoDocumento(UUID.randomUUID(),id,dto.getClientEventoId(),dto.getTipo(),dto.getJugadorId(),
                    dto.getJugadorRelacionadoId(),dto.getJugadaId(),dto.getPeriodo(),dto.getSegundosRestantes(),dto.getTipoFalta(),dto.getObservaciones(),
                    Instant.ofEpochMilli(millis),false,null);
            eventos.insert(evento);
            reconstruir(actual);
            return eventoDto(evento);
        });
    }
    public void anular(UUID id, UUID eventoId) {
        escribir(id, () -> {
            var evento = eventos.findById(eventoId).filter(e -> e.partidoId().equals(id))
                    .orElseThrow(() -> ApiException.missing("Evento no encontrado en el partido"));
            if (evento.anulado()) return false;
            var actual = partidos.findById(id).orElseThrow(() -> ApiException.missing("Partido no encontrado"));
            eventos.save(new EventoEstadisticoDocumento(evento.eventoId(),id,evento.clientEventoId(),evento.tipo(),evento.jugadorId(),
                    evento.jugadorRelacionadoId(),evento.jugadaId(),evento.periodo(),evento.segundosRestantes(),evento.tipoFalta(),evento.observaciones(),
                    evento.registradoEn(),true,Instant.now()));
            reconstruir(actual);
            return true;
        });
    }
    private void reconstruir(EstadisticasPartidoDocumento actual) {
        try {
            Map<UUID,EstadoEstadisticoJugador> jugadores = new LinkedHashMap<>();
            actual.jugadores().forEach(j -> jugadores.put(j.jugadorId(),new EstadoEstadisticoJugador(j.jugadorId(),ContadoresEstadisticos.ceros(),j.ajustes(),j.manuales())));
            EstadoSeguimientoPartido estado = EstadoSeguimientoPartido.no_iniciado;
            Integer periodo = null, segundos = null;
            for (var evento : eventos.findByPartidoIdOrderByRegistradoEnAsc(actual.partidoId())) {
                if (evento.anulado()) continue;
                if (!Objects.equals(periodo,evento.periodo())) segundos = null;
                periodo = evento.periodo();
                if (evento.segundosRestantes()!=null) segundos=evento.segundosRestantes();
                estado = switch(evento.tipo()) {
                    case inicio_partido, inicio_periodo -> EstadoSeguimientoPartido.en_directo;
                    case fin_periodo -> EstadoSeguimientoPartido.descanso;
                    case fin_partido -> EstadoSeguimientoPartido.finalizado;
                    default -> estado;
                };
                if (evento.jugadorId()!=null) {
                    var previo = jugadores.getOrDefault(evento.jugadorId(),Calculos.vacio(evento.jugadorId()));
                    jugadores.put(evento.jugadorId(),new EstadoEstadisticoJugador(evento.jugadorId(),
                            Calculos.sumar(previo.sumaEventos(),Calculos.efecto(evento.tipo())),previo.ajustes(),previo.manuales()));
                }
            }
            jugadores.values().forEach(j -> Calculos.validar(Calculos.sumar(j.sumaEventos(),j.ajustes())));
            partidos.save(copia(actual,estado,periodo,segundos,jugadores));
        } catch (ApiException | ArithmeticException ex) { throw new ApiException(409,"El evento es incompatible con las estadisticas y ajustes vigentes"); }
    }
    public List<EstadisticaJugadorPartido> historico(UUID jugador,String temporada,String competicion) {
        List<EstadisticaJugadorPartido> result = filas(temporada,competicion).stream().filter(j -> j.jugadorId().equals(jugador)).toList();
        if (result.isEmpty()) deportivo.jugador(jugador);
        return result;
    }
    public EstadisticasJugadorTemporada jugadorTemporada(UUID jugador,String temporada,String competicion,boolean incluir) {
        return Calculos.temporada(jugador,temporada,competicion,historico(jugador,temporada,competicion),incluir);
    }
    public List<EstadisticasJugadorTemporada> temporada(String temporada,String competicion) {
        Map<UUID,List<EstadisticaJugadorPartido>> grupos = new TreeMap<>();
        filas(temporada,competicion).forEach(j -> grupos.computeIfAbsent(j.jugadorId(),k -> new ArrayList<>()).add(j));
        return grupos.entrySet().stream().map(e -> Calculos.temporada(e.getKey(),temporada,competicion,e.getValue(),false)).toList();
    }
    public RankingEstadistico lideres(String temporada,String competicion,MetricaRanking metrica,int minimo,int limite) {
        var lista = temporada(temporada,competicion).stream().filter(s -> s.partidosJugados()>=minimo)
                .filter(s -> Calculos.metrica(s,metrica)!=null)
                .sorted(Comparator.<EstadisticasJugadorTemporada,Double>comparing(s -> Calculos.metrica(s,metrica)).reversed()
                        .thenComparing(s -> s.jugadorId().toString())).limit(limite).toList();
        var posiciones = new ArrayList<PosicionRanking>();
        for (int i=0;i<lista.size();i++) {
            var s=lista.get(i); posiciones.add(new PosicionRanking(i+1,s.jugadorId(),Calculos.metrica(s,metrica),s.partidosJugados()));
        }
        return new RankingEstadistico(temporada,competicion,metrica,posiciones);
    }
    private List<EstadisticaJugadorPartido> filas(String temporada,String competicion) {
        return partidos.findByTemporada(temporada).stream().filter(p -> competicion==null || competicion.equals(p.competicion()))
                .flatMap(p -> p.jugadores().stream().map(j -> Calculos.jugador(p,j)))
                .sorted(Comparator.comparing(EstadisticaJugadorPartido::fechaHora,Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(j -> j.partidoId().toString())).toList();
    }
    private <T> T escribir(UUID id,Supplier<T> action) {
        for (int intento=0;intento<5;intento++) {
            T result;
            try {
                result = tx.execute(status -> action.get());
            } catch (ApiException ex) { throw ex; }
            catch (ArithmeticException ex) { throw ApiException.bad("Contadores fuera del rango admitido"); }
            catch (RuntimeException ex) {
                boolean retry = ex instanceof OptimisticLockingFailureException || ex instanceof DuplicateKeyException;
                for (Throwable cause=ex;cause!=null;cause=cause.getCause()) {
                    if (cause instanceof MongoException mongo) {
                        if (mongo.hasErrorLabel("UnknownTransactionCommitResult")) throw new ApiException(503,"Confirmacion de escritura incierta; consulta el estado antes de reintentar");
                        retry |= mongo.hasErrorLabel("TransientTransactionError");
                    }
                }
                if (!retry) throw ex;
                if (intento==4) throw new ApiException(409,"Conflicto de escritura; vuelve a consultar el partido");
                continue;
            }
            // Un fallo al notificar nunca debe repetir una escritura ya confirmada.
            try { publisher.publishEvent(new PartidoActualizado(id)); }
            catch (RuntimeException ex) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Notificacion pendiente; WebSocket recuperara el estado mediante sondeo");
            }
            return result;
        }
        throw new IllegalStateException();
    }
    private static Map<UUID,EstadoEstadisticoJugador> mapa(EstadisticasPartidoDocumento p) {
        var result = new LinkedHashMap<UUID,EstadoEstadisticoJugador>(); p.jugadores().forEach(j -> result.put(j.jugadorId(),j)); return result;
    }
    private static EstadisticasPartidoDocumento nuevo(DeportivoClient.Partido p) {
        return new EstadisticasPartidoDocumento(p.partidoId(),p.temporada(),p.competicion(),p.jornada(),p.fechaHora(),
                EstadoSeguimientoPartido.no_iniciado,null,null,List.of(),Instant.now(),0,null);
    }
    private static EstadisticasPartidoDocumento copia(EstadisticasPartidoDocumento p,EstadoSeguimientoPartido estado,Integer periodo,Integer segundos,Map<UUID,EstadoEstadisticoJugador> jugadores) {
        return new EstadisticasPartidoDocumento(p.partidoId(),p.temporada(),p.competicion(),p.jornada(),p.fechaHora(),estado,periodo,segundos,
                new ArrayList<>(jugadores.values()),Instant.now(),p.versionPublica()+1,p.revision());
    }
    private static EventoEstadistico eventoDto(EventoEstadisticoDocumento e) {
        return new EventoEstadistico(e.clientEventoId(),e.tipo(),e.jugadorId(),e.jugadorRelacionadoId(),e.jugadaId(),e.periodo(),e.segundosRestantes(),
                e.tipoFalta(),e.observaciones(),e.eventoId(),e.partidoId(),e.registradoEn(),e.anulado(),e.anuladoEn());
    }
}
