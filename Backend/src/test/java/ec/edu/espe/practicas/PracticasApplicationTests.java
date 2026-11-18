package ec.edu.espe.practicas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_ORACLE_CONTEXT_TESTS", matches = "true")
class PracticasApplicationTests {

	@Test
	void contextLoads() {
	}

}
