package at.fhtw.docflow.document.storage;

import at.fhtw.docflow.document.application.DocumentStorage;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!bootstrap-admin")
@EnableConfigurationProperties(MinioStorageConfiguration.StorageProperties.class)
public class MinioStorageConfiguration {

    @Bean
    DocumentStorage documentStorage(StorageProperties properties) {
        if (properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new IllegalArgumentException("MINIO_ROOT_PASSWORD is required");
        }
        MinioClient client = MinioClient.builder().endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey()).build();
        var storage = new MinioDocumentStorage(client, properties.bucket());
        storage.initializeBucket();
        return storage;
    }

    @ConfigurationProperties("docflow.storage")
    public record StorageProperties(String endpoint, String accessKey, String secretKey, String bucket) {
        @Override
        public String toString() { return "StorageProperties[redacted]"; }
    }
}
