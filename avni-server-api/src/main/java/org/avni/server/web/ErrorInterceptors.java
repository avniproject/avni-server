package org.avni.server.web;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.EntityNotFoundException;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.AvniNoUserSessionException;
import org.avni.server.framework.rest.RestControllerErrorResponse;
import org.avni.server.service.exception.ConstraintViolationExceptionAcrossOrganisations;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.BugsnagReporter;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ErrorInterceptors extends ResponseEntityExceptionHandler {

    private final String maxFileSize;
    private final BugsnagReporter bugsnagReporter;
    private final ErrorBodyBuilder errorBodyBuilder;

    static class ApiError {
        private String field;
        private String message;

        public ApiError(String field, String message) {
            this.field = field;
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    @Autowired
    public ErrorInterceptors(@Value("${spring.servlet.multipart.max-file-size}") String maxFileSize, BugsnagReporter bugsnagReporter, ErrorBodyBuilder errorBodyBuilder) {
        this.maxFileSize = maxFileSize;
        this.bugsnagReporter = bugsnagReporter;
        this.errorBodyBuilder = errorBodyBuilder;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, Object> body = new HashMap<>();
        List<ApiError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(x -> new ApiError(x.getField(), x.getDefaultMessage()))
                .collect(Collectors.toList());
        body.put("errors", errors);
        return new ResponseEntity<>(body, headers, status);
    }

    private ResponseEntity <RestControllerErrorResponse> error(final Exception exception, final HttpStatus httpStatus) {
        bugsnagReporter.logAndReportToBugsnag(exception);
        final String message = Optional.ofNullable(exception.getMessage()).orElse(exception.getClass().getSimpleName());
        return new ResponseEntity(new RestControllerErrorResponse(errorBodyBuilder.getErrorBody(message)), httpStatus);
    }

    @ExceptionHandler(value = {SizeLimitExceededException.class})
    public ResponseEntity fileUploadSizeLimitExceededError(Exception e) {
        return ResponseEntity.badRequest().body(String.format("Maximum upload file size exceeded; ensure file size is less than %s.", maxFileSize));
    }

    @ExceptionHandler(value = {DataIntegrityViolationException.class, ConstraintViolationException.class, ConstraintViolationExceptionAcrossOrganisations.class})
    public ResponseEntity entityUpsertErrorDueToDataConstraintViolation(Exception e) {
        bugsnagReporter.logAndReportToBugsnag(e);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                String.format("Entity create or update failed due to constraint violation: %s",
                        errorBodyBuilder.getErrorMessageBody(e)));
    }

    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity unknownException(Exception e) {
        if (e instanceof BadRequestError) {
            return ResponseEntity.badRequest().body(errorBodyBuilder.getErrorBody(e));
        } else if (e instanceof ResourceNotFoundException || e instanceof EntityNotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBodyBuilder.getErrorMessageBody(e));
        } else if (e instanceof AvniNoUserSessionException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBodyBuilder.getErrorMessageBody(e));
        } else if (e instanceof AvniAccessException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBodyBuilder.getErrorMessageBody(e));
        } else if (e instanceof InvalidFormatException || e instanceof HttpMessageConversionException) {
            return error(e, HttpStatus.BAD_REQUEST);
        } else {
            bugsnagReporter.logAndReportToBugsnag(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        }
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        bugsnagReporter.logAndReportToBugsnag(ex);
        return super.handleExceptionInternal(ex, errorBodyBuilder.getErrorBody(body), headers, statusCode, request);
    }
}
