package com.asee.compras;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// El planificador de eventos se desactiva: este test solo comprueba que el contexto de Spring arranca.
@SpringBootTest(properties = "compras.eventos.publicador=false")
class ComprasApplicationTests {

	@Test
	void contextLoads() {
	}

}
