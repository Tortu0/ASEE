package com.asee.estadisticas.service;

import com.asee.estadisticas.dto.*;
import com.asee.estadisticas.model.*;
import com.asee.estadisticas.exception.ApiException;
import java.util.*;
import java.util.function.ToLongFunction;

/** Cálculos puros: no accede a la base de datos ni a otros servicios. */
public final class Calculos {
    private Calculos() {}
    public static ContadoresEstadisticos sumar(ContadoresEstadisticos a, ContadoresEstadisticos b) {
        return new ContadoresEstadisticos(
                Math.addExact(a.puntos(), b.puntos()),
                Math.addExact(a.tiros2Anotados(), b.tiros2Anotados()),
                Math.addExact(a.tiros2Intentados(), b.tiros2Intentados()),
                Math.addExact(a.tiros3Anotados(), b.tiros3Anotados()),
                Math.addExact(a.tiros3Intentados(), b.tiros3Intentados()),
                Math.addExact(a.tirosLibresAnotados(), b.tirosLibresAnotados()),
                Math.addExact(a.tirosLibresIntentados(), b.tirosLibresIntentados()),
                Math.addExact(a.rebotesOfensivos(), b.rebotesOfensivos()),
                Math.addExact(a.rebotesDefensivos(), b.rebotesDefensivos()),
                Math.addExact(a.asistencias(), b.asistencias()),
                Math.addExact(a.robos(), b.robos()),
                Math.addExact(a.perdidas(), b.perdidas()),
                Math.addExact(a.tapones(), b.tapones()),
                Math.addExact(a.taponesRecibidos(), b.taponesRecibidos()),
                Math.addExact(a.faltasCometidas(), b.faltasCometidas()),
                Math.addExact(a.faltasRecibidas(), b.faltasRecibidas())
        );
    }
    public static ContadoresEstadisticos restar(ContadoresEstadisticos a, ContadoresEstadisticos b) {
        return new ContadoresEstadisticos(
                Math.subtractExact(a.puntos(), b.puntos()),
                Math.subtractExact(a.tiros2Anotados(), b.tiros2Anotados()),
                Math.subtractExact(a.tiros2Intentados(), b.tiros2Intentados()),
                Math.subtractExact(a.tiros3Anotados(), b.tiros3Anotados()),
                Math.subtractExact(a.tiros3Intentados(), b.tiros3Intentados()),
                Math.subtractExact(a.tirosLibresAnotados(), b.tirosLibresAnotados()),
                Math.subtractExact(a.tirosLibresIntentados(), b.tirosLibresIntentados()),
                Math.subtractExact(a.rebotesOfensivos(), b.rebotesOfensivos()),
                Math.subtractExact(a.rebotesDefensivos(), b.rebotesDefensivos()),
                Math.subtractExact(a.asistencias(), b.asistencias()),
                Math.subtractExact(a.robos(), b.robos()),
                Math.subtractExact(a.perdidas(), b.perdidas()),
                Math.subtractExact(a.tapones(), b.tapones()),
                Math.subtractExact(a.taponesRecibidos(), b.taponesRecibidos()),
                Math.subtractExact(a.faltasCometidas(), b.faltasCometidas()),
                Math.subtractExact(a.faltasRecibidas(), b.faltasRecibidas())
        );
    }
    public static void validar(ContadoresEstadisticos c) {
        if (c.puntos() < 0 || c.tiros2Anotados() < 0 || c.tiros2Intentados() < 0 || c.tiros3Anotados() < 0 || c.tiros3Intentados() < 0 || c.tirosLibresAnotados() < 0 || c.tirosLibresIntentados() < 0 || c.rebotesOfensivos() < 0 || c.rebotesDefensivos() < 0 || c.asistencias() < 0 || c.robos() < 0 || c.perdidas() < 0 || c.tapones() < 0 || c.taponesRecibidos() < 0 || c.faltasCometidas() < 0 || c.faltasRecibidas() < 0) {
            throw ApiException.bad("Los contadores finales no pueden ser negativos");
        }
        if (c.tiros2Anotados() > c.tiros2Intentados() || c.tiros3Anotados() > c.tiros3Intentados()
                || c.tirosLibresAnotados() > c.tirosLibresIntentados()) throw ApiException.bad("Los tiros anotados superan los intentados");
        if (c.puntos() != 2L * c.tiros2Anotados() + 3L * c.tiros3Anotados() + c.tirosLibresAnotados()) {
            throw ApiException.bad("Los puntos no coinciden con las canastas y tiros libres");
        }
        if (c.faltasCometidas() > 5) throw ApiException.bad("Un jugador no puede acumular mas de cinco faltas en el partido");
        // También deben caber los campos calculados antes de confirmar la escritura.
        Math.addExact(c.rebotesOfensivos(), c.rebotesDefensivos());
        Math.addExact(c.tiros2Anotados(), c.tiros3Anotados());
        Math.addExact(c.tiros2Intentados(), c.tiros3Intentados());
    }
    public static EstadoEstadisticoJugador vacio(UUID jugador) {
        return new EstadoEstadisticoJugador(jugador, ContadoresEstadisticos.ceros(), ContadoresEstadisticos.ceros(), new DatosManualesJugador(false, 0, 0));
    }
    public static EstadoEstadisticoJugador corregir(EstadoEstadisticoJugador estado, EstadisticaJugadorPartidoUpdate dto) {
        var suma = estado.sumaEventos();
        var previo = estado.ajustes();
        var ajuste = new ContadoresEstadisticos(
                dto.getPuntos() == null ? previo.puntos() : Math.subtractExact(dto.getPuntos(), suma.puntos()),
                dto.getTiros2Anotados() == null ? previo.tiros2Anotados() : Math.subtractExact(dto.getTiros2Anotados(), suma.tiros2Anotados()),
                dto.getTiros2Intentados() == null ? previo.tiros2Intentados() : Math.subtractExact(dto.getTiros2Intentados(), suma.tiros2Intentados()),
                dto.getTiros3Anotados() == null ? previo.tiros3Anotados() : Math.subtractExact(dto.getTiros3Anotados(), suma.tiros3Anotados()),
                dto.getTiros3Intentados() == null ? previo.tiros3Intentados() : Math.subtractExact(dto.getTiros3Intentados(), suma.tiros3Intentados()),
                dto.getTirosLibresAnotados() == null ? previo.tirosLibresAnotados() : Math.subtractExact(dto.getTirosLibresAnotados(), suma.tirosLibresAnotados()),
                dto.getTirosLibresIntentados() == null ? previo.tirosLibresIntentados() : Math.subtractExact(dto.getTirosLibresIntentados(), suma.tirosLibresIntentados()),
                dto.getRebotesOfensivos() == null ? previo.rebotesOfensivos() : Math.subtractExact(dto.getRebotesOfensivos(), suma.rebotesOfensivos()),
                dto.getRebotesDefensivos() == null ? previo.rebotesDefensivos() : Math.subtractExact(dto.getRebotesDefensivos(), suma.rebotesDefensivos()),
                dto.getAsistencias() == null ? previo.asistencias() : Math.subtractExact(dto.getAsistencias(), suma.asistencias()),
                dto.getRobos() == null ? previo.robos() : Math.subtractExact(dto.getRobos(), suma.robos()),
                dto.getPerdidas() == null ? previo.perdidas() : Math.subtractExact(dto.getPerdidas(), suma.perdidas()),
                dto.getTapones() == null ? previo.tapones() : Math.subtractExact(dto.getTapones(), suma.tapones()),
                dto.getTaponesRecibidos() == null ? previo.taponesRecibidos() : Math.subtractExact(dto.getTaponesRecibidos(), suma.taponesRecibidos()),
                dto.getFaltasCometidas() == null ? previo.faltasCometidas() : Math.subtractExact(dto.getFaltasCometidas(), suma.faltasCometidas()),
                dto.getFaltasRecibidas() == null ? previo.faltasRecibidas() : Math.subtractExact(dto.getFaltasRecibidas(), suma.faltasRecibidas())
        );
        validar(sumar(suma, ajuste));
        var manual = estado.manuales();
        return new EstadoEstadisticoJugador(estado.jugadorId(), suma, ajuste, new DatosManualesJugador(
                dto.getTitular() == null ? manual.titular() : dto.getTitular(),
                dto.getSegundosJugados() == null ? manual.segundosJugados() : dto.getSegundosJugados(),
                dto.getMasMenos() == null ? manual.masMenos() : dto.getMasMenos()));
    }
    public static Set<CampoEditableEstadistica> campos(EstadisticaJugadorPartidoUpdate dto) {
        var campos = EnumSet.noneOf(CampoEditableEstadistica.class);
        if (dto.getTitular() != null) campos.add(CampoEditableEstadistica.titular);
        if (dto.getSegundosJugados() != null) campos.add(CampoEditableEstadistica.segundosJugados);
        if (dto.getPuntos() != null) campos.add(CampoEditableEstadistica.puntos);
        if (dto.getTiros2Anotados() != null) campos.add(CampoEditableEstadistica.tiros2Anotados);
        if (dto.getTiros2Intentados() != null) campos.add(CampoEditableEstadistica.tiros2Intentados);
        if (dto.getTiros3Anotados() != null) campos.add(CampoEditableEstadistica.tiros3Anotados);
        if (dto.getTiros3Intentados() != null) campos.add(CampoEditableEstadistica.tiros3Intentados);
        if (dto.getTirosLibresAnotados() != null) campos.add(CampoEditableEstadistica.tirosLibresAnotados);
        if (dto.getTirosLibresIntentados() != null) campos.add(CampoEditableEstadistica.tirosLibresIntentados);
        if (dto.getRebotesOfensivos() != null) campos.add(CampoEditableEstadistica.rebotesOfensivos);
        if (dto.getRebotesDefensivos() != null) campos.add(CampoEditableEstadistica.rebotesDefensivos);
        if (dto.getAsistencias() != null) campos.add(CampoEditableEstadistica.asistencias);
        if (dto.getRobos() != null) campos.add(CampoEditableEstadistica.robos);
        if (dto.getPerdidas() != null) campos.add(CampoEditableEstadistica.perdidas);
        if (dto.getTapones() != null) campos.add(CampoEditableEstadistica.tapones);
        if (dto.getTaponesRecibidos() != null) campos.add(CampoEditableEstadistica.taponesRecibidos);
        if (dto.getFaltasCometidas() != null) campos.add(CampoEditableEstadistica.faltasCometidas);
        if (dto.getFaltasRecibidas() != null) campos.add(CampoEditableEstadistica.faltasRecibidas);
        if (dto.getMasMenos() != null) campos.add(CampoEditableEstadistica.masMenos);
        return campos;
    }
    public static ContadoresEstadisticos efecto(TipoEventoEstadistico tipo) {
        return switch (tipo) {
            case tiro_2_anotado -> new ContadoresEstadisticos(2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case tiro_2_fallado -> new ContadoresEstadisticos(0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case tiro_3_anotado -> new ContadoresEstadisticos(3, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case tiro_3_fallado -> new ContadoresEstadisticos(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case tiro_libre_anotado -> new ContadoresEstadisticos(1, 0, 0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case tiro_libre_fallado -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            case rebote_ofensivo -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0);
            case rebote_defensivo -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0);
            case asistencia -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0);
            case robo -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0);
            case perdida -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0);
            case tapon -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0);
            case tapon_recibido -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0);
            case falta_personal -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0);
            case falta_recibida -> new ContadoresEstadisticos(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
            default -> ContadoresEstadisticos.ceros();
        };
    }
    public static double porcentaje(long anotados, long intentados) { return intentados == 0 ? 0 : 100.0 * anotados / intentados; }
    public static PorcentajesTiro porcentajes(long t2, long i2, long t3, long i3, long tl, long il) {
        return new PorcentajesTiro(porcentaje(t2,i2), porcentaje(t3,i3), porcentaje(tl,il), porcentaje(t2+t3,i2+i3));
    }
    public static MetricasAvanzadas avanzadas(long puntos, long t2, long i2, long t3, long i3, long il, long asistencias, long perdidas) {
        long intentos = i2 + i3;
        double intentosTS = 2 * (intentos + 0.44 * il);
        return new MetricasAvanzadas(intentos == 0 ? null : 100.0 * (t2 + 1.5 * t3) / intentos,
                intentosTS == 0 ? null : 100.0 * puntos / intentosTS,
                perdidas == 0 ? null : (double) asistencias / perdidas);
    }
    public static EstadisticaJugadorPartido jugador(EstadisticasPartidoDocumento partido, EstadoEstadisticoJugador estado) {
        var c = sumar(estado.sumaEventos(), estado.ajustes());
        var m = estado.manuales();
        double valoracion = (double)c.puntos() + c.rebotesOfensivos() + c.rebotesDefensivos() + c.asistencias()
                + c.robos() + c.tapones() + c.faltasRecibidas() - (c.tiros2Intentados() - c.tiros2Anotados())
                - (c.tiros3Intentados() - c.tiros3Anotados()) - (c.tirosLibresIntentados() - c.tirosLibresAnotados())
                - c.perdidas() - c.taponesRecibidos() - c.faltasCometidas();
        return new EstadisticaJugadorPartido(
                partido.partidoId(), estado.jugadorId(), partido.temporada(), partido.competicion(), partido.jornada(), partido.fechaHora(),
                m.titular(),
                m.segundosJugados(),
                c.puntos(),
                c.tiros2Anotados(),
                c.tiros2Intentados(),
                c.tiros3Anotados(),
                c.tiros3Intentados(),
                c.tirosLibresAnotados(),
                c.tirosLibresIntentados(),
                c.rebotesOfensivos(),
                c.rebotesDefensivos(),
                Math.addExact(c.rebotesOfensivos(), c.rebotesDefensivos()),
                c.asistencias(),
                c.robos(),
                c.perdidas(),
                c.tapones(),
                c.taponesRecibidos(),
                c.faltasCometidas(),
                c.faltasRecibidas(),
                m.masMenos(),
                valoracion,
                Math.addExact(c.tiros2Anotados(), c.tiros3Anotados()), Math.addExact(c.tiros2Intentados(), c.tiros3Intentados()),
                porcentajes(c.tiros2Anotados(),c.tiros2Intentados(),c.tiros3Anotados(),c.tiros3Intentados(),c.tirosLibresAnotados(),c.tirosLibresIntentados()),
                avanzadas(c.puntos(),c.tiros2Anotados(),c.tiros2Intentados(),c.tiros3Anotados(),c.tiros3Intentados(),c.tirosLibresIntentados(),c.asistencias(),c.perdidas()));
    }
    public static EstadisticasPartido partido(EstadisticasPartidoDocumento partido) {
        return new EstadisticasPartido(partido.partidoId(), partido.temporada(), partido.estadoSeguimiento(),
                partido.periodoActual(), partido.segundosRestantesPeriodo(),
                partido.jugadores().stream().map(j -> jugador(partido, j)).sorted(Comparator.comparing(j -> j.jugadorId().toString())).toList(),
                partido.actualizadoEn());
    }
    private static long suma(List<EstadisticaJugadorPartido> rows, ToLongFunction<EstadisticaJugadorPartido> field) {
        return rows.stream().mapToLong(field).reduce(0L, Math::addExact);
    }
    public static EstadisticasJugadorTemporada temporada(UUID jugador, String temporada, String competicion,
            List<EstadisticaJugadorPartido> rows, boolean incluirPartidos) {
        if (rows.isEmpty()) throw ApiException.missing("No hay estadisticas del jugador para esos filtros");
        var total = new TotalesTemporada(
                suma(rows, EstadisticaJugadorPartido::segundosJugados),
                suma(rows, EstadisticaJugadorPartido::puntos),
                suma(rows, EstadisticaJugadorPartido::tiros2Anotados),
                suma(rows, EstadisticaJugadorPartido::tiros2Intentados),
                suma(rows, EstadisticaJugadorPartido::tiros3Anotados),
                suma(rows, EstadisticaJugadorPartido::tiros3Intentados),
                suma(rows, EstadisticaJugadorPartido::tirosLibresAnotados),
                suma(rows, EstadisticaJugadorPartido::tirosLibresIntentados),
                suma(rows, EstadisticaJugadorPartido::rebotesOfensivos),
                suma(rows, EstadisticaJugadorPartido::rebotesDefensivos),
                suma(rows, EstadisticaJugadorPartido::rebotesTotales),
                suma(rows, EstadisticaJugadorPartido::asistencias),
                suma(rows, EstadisticaJugadorPartido::robos),
                suma(rows, EstadisticaJugadorPartido::perdidas),
                suma(rows, EstadisticaJugadorPartido::tapones),
                suma(rows, EstadisticaJugadorPartido::taponesRecibidos),
                suma(rows, EstadisticaJugadorPartido::faltasCometidas),
                suma(rows, EstadisticaJugadorPartido::faltasRecibidas),
                suma(rows, EstadisticaJugadorPartido::masMenos),
                rows.stream().mapToDouble(EstadisticaJugadorPartido::valoracion).sum());
        double n = rows.size();
        var promedios = new PromediosTemporada(total.segundosJugados()/60.0/n, total.puntos()/n, total.rebotesTotales()/n,
                total.asistencias()/n, total.robos()/n, total.perdidas()/n, total.tapones()/n, total.faltasCometidas()/n,
                total.masMenos()/n, total.valoracion()/n);
        return new EstadisticasJugadorTemporada(jugador, temporada, competicion, rows.size(),
                (int)rows.stream().filter(EstadisticaJugadorPartido::titular).count(), total, promedios,
                porcentajes(total.tiros2Anotados(),total.tiros2Intentados(),total.tiros3Anotados(),total.tiros3Intentados(),total.tirosLibresAnotados(),total.tirosLibresIntentados()),
                avanzadas(total.puntos(),total.tiros2Anotados(),total.tiros2Intentados(),total.tiros3Anotados(),total.tiros3Intentados(),total.tirosLibresIntentados(),total.asistencias(),total.perdidas()),
                incluirPartidos ? rows : null);
    }
    public static Double metrica(EstadisticasJugadorTemporada s, MetricaRanking metrica) {
        return switch(metrica) {
            case puntos -> s.totales().puntos().doubleValue();
            case puntos_por_partido -> s.promedios().puntosPorPartido();
            case rebotes -> s.totales().rebotesTotales().doubleValue();
            case rebotes_por_partido -> s.promedios().rebotesPorPartido();
            case asistencias -> s.totales().asistencias().doubleValue();
            case asistencias_por_partido -> s.promedios().asistenciasPorPartido();
            case robos -> s.totales().robos().doubleValue();
            case robos_por_partido -> s.promedios().robosPorPartido();
            case tapones -> s.totales().tapones().doubleValue();
            case tapones_por_partido -> s.promedios().taponesPorPartido();
            case valoracion -> s.totales().valoracion();
            case valoracion_por_partido -> s.promedios().valoracionPorPartido();
            case porcentaje_tiros_2 -> s.porcentajes().tiros2();
            case porcentaje_tiros_3 -> s.porcentajes().tiros3();
            case porcentaje_tiros_libres -> s.porcentajes().tirosLibres();
            case porcentaje_tiros_campo -> s.porcentajes().tirosCampo();
            case porcentaje_tiro_efectivo -> s.metricasAvanzadas().porcentajeTiroEfectivo();
            case true_shooting -> s.metricasAvanzadas().trueShooting();
            case mas_menos -> s.totales().masMenos().doubleValue();
        };
    }
}
