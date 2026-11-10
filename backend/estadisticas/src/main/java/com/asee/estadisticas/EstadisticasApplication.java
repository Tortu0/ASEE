package com.asee.estadisticas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class EstadisticasApplication {

    public static void main(String[] args) {
        SpringApplication.run(EstadisticasApplication.class, args);
    }
}
