package at.fhtw.docflow.document.storage;

import at.fhtw.docflow.document.application.DocumentStorage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;

import java.io.InputStream;
import java.util.UUID;

public class MinioDocumentStorage implements DocumentStorage, AutoCloseable {

    private final MinioClient client;
    private final String bucket;

    public MinioDocumentStorage(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    void initializeBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (MinioException exception) {
            throw new StorageException(exception);
        }
    }

    public String store(InputStream content, long size) {
        String key = "documents/" + UUID.randomUUID() + ".pdf";
        try {
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                    .stream(content, size, -1L).contentType("application/pdf").build());
            return key;
        } catch (MinioException exception) {
            throw new StorageException(exception);
        }
    }

    public InputStream read(String key) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException exception) {
            throw new StorageException(exception);
        }
    }

    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (MinioException exception) {
            throw new StorageException(exception);
        }
    }

    public void close() throws Exception {
        client.close();
    }
}
