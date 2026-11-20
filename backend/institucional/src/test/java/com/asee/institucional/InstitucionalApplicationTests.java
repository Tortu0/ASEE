package com.asee.institucional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.datos-ejemplo=false",
		"jwt.secret=01234567891011121314151617181920"
})
class InstitucionalApplicationTests {

	@Test
	void contextLoads() {
	}

}
