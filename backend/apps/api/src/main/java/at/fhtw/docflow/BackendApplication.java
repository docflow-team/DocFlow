package at.fhtw.docflow;

import at.fhtw.docflow.document.application.CategoryService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Profiles;

@SpringBootApplication
public class BackendApplication {

    @Bean
    @Profile("!bootstrap-admin")
    ApplicationRunner seedCategories(CategoryService categories) {
        return args -> categories.seedDefaults();
    }

	public static void main(String[] args) {

		var context = SpringApplication.run(BackendApplication.class, args);
		if (context.getEnvironment().acceptsProfiles(Profiles.of("bootstrap-admin"))) {
			context.close();
		}
	}

}
