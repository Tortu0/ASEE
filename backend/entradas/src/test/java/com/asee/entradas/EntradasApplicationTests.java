package com.asee.entradas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"entradas.carga=",
		"spring.task.scheduling.enabled=false",
		"spring.data.mongodb.auto-index-creation=false"
})
class EntradasApplicationTests {

	@Test
	void contextLoads() {
	}

}