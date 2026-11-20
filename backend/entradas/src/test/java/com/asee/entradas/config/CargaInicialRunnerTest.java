package com.asee.entradas.config;

import com.asee.entradas.entity.Asiento;
import com.asee.entradas.entity.EstadoAsiento;
import com.asee.entradas.entity.Tarifa;
import com.asee.entradas.entity.TipoEntrada;
import com.asee.entradas.repository.AsientoRepository;
import com.asee.entradas.repository.TarifaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CargaInicialRunnerTest {

    @Mock AsientoRepository asientoRepo;
    @Mock TarifaRepository tarifaRepo;
    @Captor ArgumentCaptor<List<Asiento>> asientosCaptor;
    @Captor ArgumentCaptor<List<Tarifa>> tarifasCaptor;

    private CargaInicialRunner runner(List<EntradasProperties.CargaPartido> carga) {
        EntradasProperties props = new EntradasProperties(
                10, new EntradasProperties.RabbitProps("club.exchange", "entrada.comprada"), carga);
        return new CargaInicialRunner(props, asientoRepo, tarifaRepo);
    }

    private static EntradasProperties.CargaPartido partido(String id) {
        Map<String, EntradasProperties.PreciosZona> precios = new LinkedHashMap<>();
        precios.put("A", new EntradasProperties.PreciosZona(8.5, 6.0, 20.0));
        precios.put("B", new EntradasProperties.PreciosZona(6.5, 4.5, 15.0));
        return new EntradasProperties.CargaPartido(id,
                List.of(new EntradasProperties.ZonaConfig("A", 2, 3), new EntradasProperties.ZonaConfig("B", 1, 2)),
                precios);
    }

    @Test
    void sinConfiguracionDeCarga_noHaceNada() {
        runner(null).run(null);

        verifyNoInteractions(asientoRepo, tarifaRepo);
    }

    @Test
    void siElPartidoYaTieneAsientos_seOmiteLaCarga() {
        when(asientoRepo.existsByPartidoId("p1")).thenReturn(true);

        runner(List.of(partido("p1"))).run(null);

        verify(asientoRepo, never()).saveAll(any());
        verifyNoInteractions(tarifaRepo);
    }

    @Test
    void creaTodosLosAsientosLibresDeCadaZona() {
        when(asientoRepo.existsByPartidoId("p1")).thenReturn(false);

        runner(List.of(partido("p1"))).run(null);

        verify(asientoRepo).saveAll(asientosCaptor.capture());
        List<Asiento> asientos = asientosCaptor.getValue();
        assertThat(asientos).hasSize(8);   // A: 2 filas × 3 + B: 1 fila × 2
        assertThat(asientos).allSatisfy(a -> {
            assertThat(a.getPartidoId()).isEqualTo("p1");
            assertThat(a.getEstado()).isEqualTo(EstadoAsiento.libre);
            assertThat(a.getAsientoId()).isNotBlank();
            assertThat(a.getVersion()).isNull();
        });
        assertThat(asientos).extracting(Asiento::getAsientoId).doesNotHaveDuplicates();
        assertThat(asientos).filteredOn(a -> a.getZona().equals("A")).hasSize(6)
                .allSatisfy(a -> {
                    assertThat(a.getFila()).isBetween(1, 2);
                    assertThat(a.getNumero()).isBetween(1, 3);
                });
        assertThat(asientos).filteredOn(a -> a.getZona().equals("B")).hasSize(2)
                .extracting(Asiento::getNumero).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void creaUnaTarifaPorZonaYTipoDeEntrada() {
        when(asientoRepo.existsByPartidoId("p1")).thenReturn(false);

        runner(List.of(partido("p1"))).run(null);

        verify(tarifaRepo).saveAll(tarifasCaptor.capture());
        List<Tarifa> tarifas = tarifasCaptor.getValue();
        assertThat(tarifas).hasSize(6);   // 2 zonas × 3 tipos
        assertThat(tarifas).allSatisfy(t -> assertThat(t.getPartidoId()).isEqualTo("p1"));
        assertThat(precio(tarifas, "A", TipoEntrada.general)).isEqualTo(8.5);
        assertThat(precio(tarifas, "A", TipoEntrada.socio)).isEqualTo(6.0);
        assertThat(precio(tarifas, "A", TipoEntrada.vip)).isEqualTo(20.0);
        assertThat(precio(tarifas, "B", TipoEntrada.general)).isEqualTo(6.5);
        assertThat(precio(tarifas, "B", TipoEntrada.socio)).isEqualTo(4.5);
        assertThat(precio(tarifas, "B", TipoEntrada.vip)).isEqualTo(15.0);
    }

    @Test
    void sinPrecios_creaLosAsientosPeroNoLasTarifas() {
        when(asientoRepo.existsByPartidoId("p1")).thenReturn(false);
        EntradasProperties.CargaPartido sinPrecios = new EntradasProperties.CargaPartido(
                "p1", List.of(new EntradasProperties.ZonaConfig("A", 1, 1)), null);

        runner(List.of(sinPrecios)).run(null);

        verify(asientoRepo).saveAll(any());
        verifyNoInteractions(tarifaRepo);
    }

    @Test
    void procesaCadaPartidoPorSeparado() {
        when(asientoRepo.existsByPartidoId("p1")).thenReturn(true);
        when(asientoRepo.existsByPartidoId("p2")).thenReturn(false);

        runner(List.of(partido("p1"), partido("p2"))).run(null);

        verify(asientoRepo).saveAll(asientosCaptor.capture());
        assertThat(asientosCaptor.getValue()).extracting(Asiento::getPartidoId).containsOnly("p2");
    }

    private static double precio(List<Tarifa> tarifas, String zona, TipoEntrada tipo) {
        return tarifas.stream()
                .filter(t -> t.getZona().equals(zona) && t.getTipoEntrada() == tipo)
                .findFirst().orElseThrow().getPrecio();
    }
}
