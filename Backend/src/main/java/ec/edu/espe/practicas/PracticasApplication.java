package ec.edu.espe.practicas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PracticasApplication {

	public static void main(String[] args) {
		if (args.length > 0 && "--crear-admin-general".equals(args[0])) {
			System.exit(ec.edu.espe.practicas.bootstrap.FirstAdministratorCommand.run(args));
			return;
		}
		SpringApplication.run(PracticasApplication.class, args);
	}

}
