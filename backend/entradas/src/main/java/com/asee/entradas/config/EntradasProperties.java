package com.asee.entradas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Propiedades de configuración del microservicio de Entradas leídas desde
 * {@code application.yaml}.
 *
 * La sección {@code entradas.carga} contiene la configuración inicial de
 * asientos y tarifas por partido que se carga al arrancar la aplicación.
 */
@ConfigurationProperties(prefix = "entradas")
public record EntradasProperties(
        int reservaTtlMinutes,
        RabbitProps rabbit,
        List<CargaPartido> carga
) {

    public record RabbitProps(String exchange, String routingKey) {}

    public record CargaPartido(
            String partidoId,
            List<ZonaConfig> zonas,
            Map<String, PreciosZona> precios
    ) {}

    public record ZonaConfig(String nombre, int filas, int asientosPorFila) {}

    /** Precios por tipo de entrada en una zona: general, socio, vip. */
    public record PreciosZona(double general, double socio, double vip) {}
}
