package at.fhtw.docflow.document.application;

import at.fhtw.docflow.document.domain.Document;
import at.fhtw.docflow.document.domain.Document.InvalidDocumentException;
import at.fhtw.docflow.document.domain.Document.InvalidDocumentException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import at.fhtw.docflow.document.domain.Category;
import static at.fhtw.docflow.document.DocumentTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentServiceTest {
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final DocumentService service = new DefaultDocumentService(documents, categories, storage, () -> 1L, transactions);
    private final Document.Metadata metadata = new Document.Metadata("Invoice", null);

    @BeforeEach
    void transactionStatus() {
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    void uploadsValidatedPdfUnderTheCurrentOwner(int pages) {
        when(storage.store(any(), anyLong())).thenReturn("documents/uploaded.pdf");
        when(documents.save(any())).thenAnswer(call -> call.getArgument(0));
        byte[] content = pdf(pages);
        Document result = service.upload(metadata, "C:\\fakepath\\invoice.pdf", "application/pdf", content);
        assertEquals(1L, result.getOwnerId());
        assertEquals("invoice.pdf", result.getOriginalFilename());
        assertEquals(content.length, result.getSizeBytes());
        assertEquals("documents/uploaded.pdf", result.getObjectKey());
        var order = inOrder(storage, documents, transactions);
        order.verify(storage).store(any(), eq((long) content.length));
        order.verify(documents).save(any());
        order.verify(transactions).commit(any());
    }

    @Test
    void rejectsEmptyMalformedEncryptedAndOutOfRangePdfsBeforeStorage() {
        for (byte[] content : List.of(new byte[0], new byte[]{1, 2}, pdf(0), pdf(51), pdf(1, "secret"), pdf(1, ""))) {
            assertThrows(InvalidDocumentException.class, () -> service.upload(metadata, "file.pdf", "application/pdf", content));
        }
        verifyNoInteractions(storage, documents);
    }

    @Test
    void rejectsOversizedFilesAndUnsupportedMediaTypesBeforeStorage() {
        var size = assertThrows(InvalidDocumentException.class,
                () -> service.upload(metadata, "file.pdf", "application/pdf", new byte[Document.MAX_PDF_BYTES + 1]));
        assertEquals(Reason.TOO_LARGE, size.reason());
        var type = assertThrows(InvalidDocumentException.class,
                () -> service.upload(metadata, "file.pdf", "text/plain", pdf(1)));
        assertEquals(Reason.UNSUPPORTED_TYPE, type.reason());
        verifyNoInteractions(storage, documents);
    }

    @Test
    void aFailedCommitRemovesTheUploadedObject() {
        when(storage.store(any(), anyLong())).thenReturn("documents/uploaded.pdf");
        when(documents.save(any())).thenAnswer(call -> call.getArgument(0));
        doThrow(new TransactionSystemException("Synthetic commit failure")).when(transactions).commit(any());
        assertThrows(TransactionSystemException.class, () -> service.upload(metadata, "file.pdf", "application/pdf", pdf(1)));
        verify(storage).delete("documents/uploaded.pdf");
    }

    @Test
    void cleanupFailureDoesNotHideTheDatabaseFailure() {
        when(storage.store(any(), anyLong())).thenReturn("documents/uploaded.pdf");
        var databaseFailure = new IllegalStateException("Synthetic database failure");
        when(documents.save(any())).thenThrow(databaseFailure);
        doThrow(new DocumentStorage.StorageException(new IOException("Synthetic storage failure")))
                .when(storage).delete("documents/uploaded.pdf");
        assertSame(databaseFailure, assertThrows(IllegalStateException.class,
                () -> service.upload(metadata, "file.pdf", "application/pdf", pdf(1))));
        verify(transactions).rollback(any());
    }

    @Test
    void listsAreOwnerScopedAndPaginationIsBounded() {
        var page = new DocumentService.DocumentPage(List.of(document(1, 1)), 0, 20, 1, 1);
        when(documents.findAllByOwnerId(1L, 0, 20)).thenReturn(page);
        assertEquals(page, service.getAllDocuments(0, 20));
        assertThrows(InvalidDocumentException.class, () -> service.getAllDocuments(-1, 20));
        assertThrows(InvalidDocumentException.class, () -> service.getAllDocuments(0, 0));
        assertThrows(InvalidDocumentException.class, () -> service.getAllDocuments(0, 101));
    }

    @Test
    void metadataEditsPreserveFileAndServerControlledFields() {
        Document original = document(1, 1);
        original.replaceCategories(List.of(new Category(2L, "Invoice", null)));
        when(documents.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(original));
        when(documents.save(original)).thenReturn(original);
        Document result = service.updateMetadata(1L, new Document.Metadata("Renamed", null));
        assertEquals("Renamed", result.getTitle());
        assertNull(result.getDescription());
        assertEquals("documents/1.pdf", result.getObjectKey());
        assertEquals("original.pdf", result.getOriginalFilename());
        assertEquals(1L, result.getOwnerId());
        assertEquals(2L, result.getCategories().getFirst().id());
        verifyNoInteractions(storage);
    }

    @Test
    void downloadsAreOwnerScopedAndCloseTheStorageStream() throws Exception {
        when(documents.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(document(1, 1)));
        byte[] content = pdf(1);
        var stream = spy(new ByteArrayInputStream(content));
        when(storage.read("documents/1.pdf")).thenReturn(stream);
        var result = service.download(1L);
        assertArrayEquals(content, result.content());
        assertEquals("original.pdf", result.originalFilename());
        verify(stream).close();
    }

    @Test
    void missingOrForeignDocumentsNeverAccessStorage() {
        assertThrows(NoSuchElementException.class, () -> service.getDocumentById(7L));
        assertThrows(NoSuchElementException.class, () -> service.download(7L));
        assertThrows(NoSuchElementException.class, () -> service.updateMetadata(7L, metadata));
        assertThrows(NoSuchElementException.class, () -> service.deleteDocument(7L));
        verifyNoInteractions(storage);
    }

    @Test
    void objectDeletionRunsAfterTheDatabaseCommit() {
        when(documents.deleteByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(document(1, 1)));
        service.deleteDocument(1L);
        var order = inOrder(documents, transactions, storage);
        order.verify(documents).deleteByIdAndOwnerId(1L, 1L);
        order.verify(transactions).commit(any());
        order.verify(storage).delete("documents/1.pdf");
    }

    @Test
    void failedDatabaseDeletionDoesNotDeleteTheObject() {
        when(documents.deleteByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(document(1, 1)));
        doThrow(new TransactionSystemException("Synthetic commit failure")).when(transactions).commit(any());
        assertThrows(TransactionSystemException.class, () -> service.deleteDocument(1L));
        verifyNoInteractions(storage);
    }

    @Test
    void storageCleanupFailureLeavesDeletionSuccessfulForManualRecovery() {
        when(documents.deleteByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(document(1, 1)));
        doThrow(new DocumentStorage.StorageException(new IOException("Synthetic storage failure")))
                .when(storage).delete("documents/1.pdf");
        assertDoesNotThrow(() -> service.deleteDocument(1L));
    }

    @Test
    void categoryReplacementRequiresAnOwnerAndValidUniqueKnownIds() {
        Document original = document(1, 1);
        var invoice = new Category(2L, "Invoice", "Amount requested");
        original.replaceCategories(List.of(invoice));
        when(documents.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(original));
        when(categories.findAllById(List.of(2L))).thenReturn(List.of(invoice));
        when(documents.save(original)).thenReturn(original);
        assertEquals(List.of(invoice), service.replaceCategories(1L, List.of(2L)).getCategories());
        for (List<Long> ids : List.of(List.of(2L, 2L), List.of(0L), List.of(99L))) {
            assertThrows(InvalidDocumentException.class, () -> service.replaceCategories(1L, ids));
            assertEquals(List.of(invoice), original.getCategories());
        }
        assertThrows(InvalidDocumentException.class, () -> service.replaceCategories(1L, null));
        assertThrows(NoSuchElementException.class, () -> service.replaceCategories(9L, List.of(2L)));
        assertTrue(service.replaceCategories(1L, List.of()).getCategories().isEmpty());
        verify(documents, times(2)).save(original);
        verifyNoInteractions(storage);
    }

    @Test
    void tagReplacementIsOwnerScopedAndDoesNotSaveInvalidInputOrTouchStorage() {
        var original = document(1, 1);
        original.replaceCategories(List.of(new Category(2L, "Invoice", null)));
        when(documents.findByIdAndOwnerId(1L, 1L)).thenReturn(Optional.of(original));
        when(documents.save(original)).thenReturn(original);
        var result = service.replaceTags(1L, List.of(" Housing ", "housing"));
        assertEquals(List.of("housing"), result.getTags());
        assertEquals(2L, result.getCategories().getFirst().id());
        assertEquals("Original", result.getTitle());
        assertEquals("documents/1.pdf", result.getObjectKey());
        assertThrows(InvalidDocumentException.class, () -> service.replaceTags(1L, List.of("valid", " ")));
        assertEquals(List.of("housing"), original.getTags());
        assertThrows(NoSuchElementException.class, () -> service.replaceTags(9L, List.of("housing")));
        assertTrue(service.replaceTags(1L, List.of()).getTags().isEmpty());
        verify(documents, times(2)).save(original);
        verifyNoInteractions(categories, storage);
    }
}
