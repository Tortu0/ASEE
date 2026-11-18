package com.asee.compras;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // reintento periódico de los eventos pendientes de publicar
public class ComprasApplication {

	public static void main(String[] args) {
		SpringApplication.run(ComprasApplication.class, args);
	}

}
