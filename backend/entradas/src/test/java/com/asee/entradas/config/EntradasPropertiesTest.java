package com.asee.entradas.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Comprueba que la sección {@code entradas} del application.yaml real se enlaza bien con {@link EntradasProperties}. */
class EntradasPropertiesTest {

    private EntradasProperties cargar() throws Exception {
        List<PropertySource<?>> fuentes =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yaml"));
        return new Binder(ConfigurationPropertySources.from(fuentes.get(0)))
                .bind("entradas", EntradasProperties.class)
                .get();
    }

    @Test
    void enlazaElTtlDeLaReservaYLaConfiguracionDeRabbit() throws Exception {
        EntradasProperties p = cargar();

        assertThat(p.reservaTtlMinutes()).isEqualTo(10);
        assertThat(p.rabbit().exchange()).isEqualTo("club.exchange");
        assertThat(p.rabbit().routingKey()).isEqualTo("entrada.comprada");
    }

    @Test
    void enlazaLaCargaInicialDeLosPartidos() throws Exception {
        EntradasProperties p = cargar();

        assertThat(p.carga()).hasSize(2);
        EntradasProperties.CargaPartido primero = p.carga().get(0);
        assertThat(primero.partidoId()).isEqualTo("20000000-0000-0000-0000-000000000004");
        assertThat(primero.zonas()).containsExactly(
                new EntradasProperties.ZonaConfig("A", 10, 30),
                new EntradasProperties.ZonaConfig("B", 10, 30));
        assertThat(primero.precios()).containsOnlyKeys("A", "B");
        assertThat(primero.precios().get("A")).isEqualTo(new EntradasProperties.PreciosZona(8.5, 6.0, 20.0));
        assertThat(primero.precios().get("B")).isEqualTo(new EntradasProperties.PreciosZona(6.5, 4.5, 15.0));
        assertThat(p.carga().get(1).partidoId()).isEqualTo("20000000-0000-0000-0000-000000000007");
    }
}
