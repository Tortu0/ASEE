package com.asee.entradas.config;

import com.asee.entradas.entity.Asiento;
import com.asee.entradas.entity.EstadoAsiento;
import com.asee.entradas.entity.Tarifa;
import com.asee.entradas.entity.TipoEntrada;
import com.asee.entradas.repository.AsientoRepository;
import com.asee.entradas.repository.TarifaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Carga inicial de asientos y tarifas para cada partido definido en la
 * configuración {@code entradas.carga}.
 *
 * Solo inserta datos si el partido no tiene asientos ya en MongoDB, de modo
 * que los reinicios del servicio son idempotentes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CargaInicialRunner implements ApplicationRunner {

    private final EntradasProperties props;
    private final AsientoRepository asientoRepository;
    private final TarifaRepository tarifaRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (props.carga() == null) return;

        for (EntradasProperties.CargaPartido carga : props.carga()) {
            String partidoId = carga.partidoId();

            // Idempotencia: si ya hay asientos, no volver a cargar
            if (asientoRepository.existsByPartidoId(partidoId)) {
                log.info("Partido {} ya tiene asientos; se omite la carga inicial.", partidoId);
                continue;
            }

            log.info("Cargando asientos y tarifas para partido {}", partidoId);

            List<Asiento> asientos = new ArrayList<>();
            for (EntradasProperties.ZonaConfig zona : carga.zonas()) {
                for (int fila = 1; fila <= zona.filas(); fila++) {
                    for (int num = 1; num <= zona.asientosPorFila(); num++) {
                        Asiento a = new Asiento(
                                UUID.randomUUID().toString(),
                                null,
                                partidoId,
                                zona.nombre(),
                                fila,
                                num,
                                EstadoAsiento.libre
                        );
                        asientos.add(a);
                    }
                }
            }
            asientoRepository.saveAll(asientos);
            log.info("  → {} asientos creados", asientos.size());

            // Tarifas
            if (carga.precios() != null) {
                List<Tarifa> tarifas = new ArrayList<>();
                carga.precios().forEach((zona, precios) -> {
                    for (TipoEntrada tipo : TipoEntrada.values()) {
                        double precio = switch (tipo) {
                            case general -> precios.general();
                            case socio   -> precios.socio();
                            case vip     -> precios.vip();
                        };
                        tarifas.add(new Tarifa(
                                UUID.randomUUID().toString(),
                                partidoId,
                                zona,
                                tipo,
                                precio
                        ));
                    }
                });
                tarifaRepository.saveAll(tarifas);
                log.info("  → {} tarifas creadas", tarifas.size());
            }
        }
    }
}
