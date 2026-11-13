package at.fhtw.docflow.document.storage;

import at.fhtw.docflow.document.application.DocumentStorage.StorageException;
import at.fhtw.docflow.document.storage.MinioStorageConfiguration.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MinioDocumentStorageTest {

    private final MinioClient client = mock(MinioClient.class);
    private final MinioDocumentStorage storage = new MinioDocumentStorage(client, "test-documents");

    @Test
    void createsAMissingBucketButLeavesExistingBucketsAlone() throws Exception {
        when(client.bucketExists(any(BucketExistsArgs.class))).thenReturn(false, true);

        storage.initializeBucket();
        storage.initializeBucket();

        var created = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(client).makeBucket(created.capture());
        assertEquals("test-documents", created.getValue().bucket());
    }

    @Test
    void uploadsUseDistinctServerGeneratedKeysInTheConfiguredBucket() throws Exception {
        byte[] content = {1, 2, 3};
        String first = storage.store(new ByteArrayInputStream(content), content.length);
        String second = storage.store(new ByteArrayInputStream(content), content.length);

        assertNotEquals(first, second);
        assertTrue(first.startsWith("documents/"));
        assertTrue(first.endsWith(".pdf"));
        assertDoesNotThrow(() -> UUID.fromString(first.substring(10, first.length() - 4)));
        var uploads = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(client, times(2)).putObject(uploads.capture());
        assertEquals(first, uploads.getAllValues().getFirst().object());
        assertEquals("test-documents", uploads.getAllValues().getFirst().bucket());
        assertEquals("application/pdf", uploads.getAllValues().getFirst().contentType().toString());
        assertEquals((long) content.length, uploads.getAllValues().getFirst().objectSize());
    }

    @Test
    void readsAndDeletesUseTheStoredKeyAndConfiguredBucket() throws Exception {
        String key = "documents/existing.pdf";
        GetObjectResponse content = mock(GetObjectResponse.class);
        when(client.getObject(any(GetObjectArgs.class))).thenReturn(content);

        assertSame(content, storage.read(key));
        storage.delete(key);

        var read = ArgumentCaptor.forClass(GetObjectArgs.class);
        var deleted = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client).getObject(read.capture());
        verify(client).removeObject(deleted.capture());
        assertEquals(key, read.getValue().object());
        assertEquals(key, deleted.getValue().object());
        assertEquals("test-documents", read.getValue().bucket());
        assertEquals("test-documents", deleted.getValue().bucket());
    }

    @Test
    void clientFailuresBecomeSafeStorageErrors() throws Exception {
        MinioException cause = mock(MinioException.class);
        when(client.getObject(any(GetObjectArgs.class))).thenThrow(cause);

        var error = assertThrows(StorageException.class, () -> storage.read("documents/missing.pdf"));

        assertEquals("Document storage is unavailable", error.getMessage());
        assertSame(cause, error.getCause());
    }

    @Test
    void configurationDoesNotExposeCredentialsInItsRepresentation() {
        var properties = new StorageProperties("http://localhost:9000", "test-user", "synthetic-secret", "test-documents");
        assertFalse(properties.toString().contains(properties.accessKey()));
        assertFalse(properties.toString().contains(properties.secretKey()));
    }
}
