package com.asee.estadisticas.service;

import com.asee.estadisticas.dto.EstadisticaJugadorPartidoUpdate;
import com.asee.estadisticas.model.*;
import com.asee.estadisticas.exception.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalculosTest {
    @Test void tirosAnotadosTambienSonIntentos() {
        var dos=Calculos.efecto(TipoEventoEstadistico.tiro_2_anotado);
        var tres=Calculos.efecto(TipoEventoEstadistico.tiro_3_anotado);
        var libre=Calculos.efecto(TipoEventoEstadistico.tiro_libre_anotado);
        var total=Calculos.sumar(Calculos.sumar(dos,tres),libre);
        assertThat(total.puntos()).isEqualTo(6);
        assertThat(total.tiros2Intentados()).isEqualTo(1);
        assertThat(total.tiros3Intentados()).isEqualTo(1);
        assertThat(total.tirosLibresIntentados()).isEqualTo(1);
        assertThatCode(() -> Calculos.validar(total)).doesNotThrowAnyException();
    }
    @Test void correccionSustituyeAjusteYNoLoAcumula() {
        var jugador=Calculos.vacio(UUID.randomUUID());
        jugador=new EstadoEstadisticoJugador(jugador.jugadorId(),Calculos.efecto(TipoEventoEstadistico.tiro_2_anotado),jugador.ajustes(),jugador.manuales());
        var dto=new EstadisticaJugadorPartidoUpdate(); dto.setPuntos(4); dto.setTiros2Anotados(2); dto.setTiros2Intentados(2); dto.setSegundosJugados(600); dto.setMasMenos(-3);
        var primero=Calculos.corregir(jugador,dto);
        var segundo=Calculos.corregir(primero,dto);
        assertThat(segundo).isEqualTo(primero);
        var eventoNuevo=Calculos.sumar(segundo.sumaEventos(),Calculos.efecto(TipoEventoEstadistico.tiro_3_anotado));
        assertThat(Calculos.sumar(eventoNuevo,segundo.ajustes()).puntos()).isEqualTo(7);
        assertThat(segundo.manuales().segundosJugados()).isEqualTo(600);
    }
    @Test void rechazaPuntosIncompatiblesConTiros() {
        var dto=new EstadisticaJugadorPartidoUpdate(); dto.setPuntos(5);
        assertThatThrownBy(() -> Calculos.corregir(Calculos.vacio(UUID.randomUUID()),dto)).isInstanceOf(ApiException.class);
    }
    @Test void metricasSeCalculanConDenominadoresCorrectos() {
        var porcentajes=Calculos.porcentajes(5,10,2,5,3,4);
        assertThat(porcentajes.tiros2()).isEqualTo(50.0);
        assertThat(porcentajes.tiros3()).isEqualTo(40.0);
        var avanzadas=Calculos.avanzadas(19,5,10,2,5,4,6,3);
        assertThat(avanzadas.porcentajeTiroEfectivo()).isCloseTo(100.0*8/15,within(0.000001));
        assertThat(avanzadas.trueShooting()).isCloseTo(100.0*19/(2*(15+0.44*4)),within(0.000001));
        assertThat(avanzadas.ratioAsistenciasPerdidas()).isEqualTo(2.0);
        assertThat(Calculos.porcentajes(0,0,0,0,0,0).tirosCampo()).isZero();
        assertThat(Calculos.avanzadas(0,0,0,0,0,0,0,0).trueShooting()).isNull();
    }
}
