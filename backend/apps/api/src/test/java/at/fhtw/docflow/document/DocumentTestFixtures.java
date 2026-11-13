package at.fhtw.docflow.document;

import at.fhtw.docflow.document.application.DocumentStorage;
import at.fhtw.docflow.document.domain.Document;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public final class DocumentTestFixtures {
    private DocumentTestFixtures() { }

    public static byte[] pdf(int pages) { return pdf(pages, null); }

    public static byte[] pdf(int pages, String password) {
        try (var pdf = new PDDocument(); var output = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) pdf.addPage(new PDPage());
            if (password != null) {
                pdf.protect(new StandardProtectionPolicy("owner-password", password, new AccessPermission()));
            }
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static Document document(long id, long owner) {
        Instant created = Instant.parse("2026-11-13T12:00:00Z");
        return new Document(id, owner, new Document.Metadata("Original", "Original description"),
                "original.pdf", "documents/" + id + ".pdf", 512, created, created, Set.of(), Set.of());
    }

    public static Map<String, byte[]> mockStorage(DocumentStorage storage) {
        Map<String, byte[]> objects = new HashMap<>();
        when(storage.store(any(InputStream.class), anyLong())).thenAnswer(call -> {
            String key = "documents/" + UUID.randomUUID() + ".pdf";
            objects.put(key, ((InputStream) call.getArgument(0)).readAllBytes());
            return key;
        });
        when(storage.read(anyString())).thenAnswer(call -> {
            byte[] content = objects.get(call.getArgument(0));
            if (content == null) throw new DocumentStorage.StorageException(new IOException("Missing test object"));
            return new ByteArrayInputStream(content);
        });
        doAnswer(call -> { objects.remove(call.getArgument(0)); return null; }).when(storage).delete(anyString());
        return objects;
    }
}
