package at.fhtw.docflow.document.application;

import java.io.InputStream;

public interface DocumentStorage {

    // Callers close both supplied upload streams and returned download streams.
    String store(InputStream content, long size);

    InputStream read(String key);

    void delete(String key);

    class StorageException extends RuntimeException {
        public StorageException(Throwable cause) {
            super("Document storage is unavailable", cause);
        }
    }
}
