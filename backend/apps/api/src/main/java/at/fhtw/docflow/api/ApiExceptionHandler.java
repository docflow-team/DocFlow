package at.fhtw.docflow.api;

import java.util.NoSuchElementException;
import java.util.Map;
import at.fhtw.docflow.identity.application.AuthenticationService.InvalidLoginException;
import at.fhtw.docflow.identity.application.AuthenticationService.InvalidPasswordException;
import at.fhtw.docflow.identity.application.AuthenticationService.InvalidAccountException;
import at.fhtw.docflow.document.domain.Document.InvalidDocumentException;
import at.fhtw.docflow.document.application.DocumentStorage.StorageException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.putAll(headers);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(body, jsonHeaders, status);
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ResponseEntity<Object> handleInvalidLogin(InvalidLoginException exception, WebRequest request) {
        return handleExceptionInternal(exception, ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                exception.getMessage()), new HttpHeaders(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Object> handleInvalidPassword(InvalidPasswordException exception, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setProperty("errors", Map.of(exception.field(), exception.getMessage()));
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request fields");
        var errors = new java.util.LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Object> handleMissingResource(NoSuchElementException exception, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Document or document type not found");
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(InvalidDocumentException.class)
    public ResponseEntity<Object> handleInvalidDocument(InvalidDocumentException exception, WebRequest request) {
        HttpStatus status = switch (exception.reason()) {
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNSUPPORTED_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case INVALID -> HttpStatus.BAD_REQUEST;
        };
        var problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        return handleExceptionInternal(exception, problem, new HttpHeaders(), status, request);
    }

    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Object> handleStorageFailure(StorageException exception, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Document storage is unavailable");
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "PDF must not exceed 10 MiB; multipart requests must not exceed 12 MiB");
        return handleExceptionInternal(exception, problem, headers, HttpStatus.CONTENT_TOO_LARGE, request);
    }

    // Database constraints enforce unique identifiers, including competing creates.
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleStoredConflict(org.springframework.dao.DataIntegrityViolationException exception, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The request conflicts with stored data");
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(InvalidAccountException.class)
    public ResponseEntity<Object> handleInvalidAccount(InvalidAccountException exception, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Object> handleForbidden(org.springframework.security.access.AccessDeniedException exception, WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access is forbidden");
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.FORBIDDEN, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        log.error("Unexpected API error", exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        return handleExceptionInternal(exception, problem, new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR, request);
    }
}
