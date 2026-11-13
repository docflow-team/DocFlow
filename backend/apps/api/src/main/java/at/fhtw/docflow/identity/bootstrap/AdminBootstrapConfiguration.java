package at.fhtw.docflow.identity.bootstrap;

import at.fhtw.docflow.identity.application.AdminBootstrapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("bootstrap-admin")
@EnableConfigurationProperties(BootstrapAdminProperties.class)
public class AdminBootstrapConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfiguration.class);

    @Bean
    ApplicationRunner adminBootstrapRunner(AdminBootstrapService service,
                                           BootstrapAdminProperties properties) {
        return args -> {
            if (service.bootstrap(properties.username(), properties.email(), properties.password())) {
                log.info("Initial administrator created; password change is required at first login");
            } else {
                log.info("Admin bootstrap skipped because accounts already exist");
            }
        };
    }
}
