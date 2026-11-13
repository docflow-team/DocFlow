package at.fhtw.docflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {

		var context = SpringApplication.run(BackendApplication.class, args);
		if (context.getEnvironment().acceptsProfiles(Profiles.of("bootstrap-admin"))) {
			context.close();
		}
	}

}
