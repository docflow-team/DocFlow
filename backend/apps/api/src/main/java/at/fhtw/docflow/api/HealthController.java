package at.fhtw.docflow.api;

import at.fhtw.docflow.api.HealthResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping(value = "/api/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthResponse health() {
        return new HealthResponse("UP", "DocFlow REST Server is running!");
    }
}
