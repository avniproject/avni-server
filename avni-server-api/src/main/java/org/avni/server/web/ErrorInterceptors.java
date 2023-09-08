package org.avni.server.web;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.AvniNoUserSessionException;
import org.avni.server.framework.rest.RestControllerErrorResponse;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.Bugsnag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private Bugsnag bugsnag;

    class ApiError {
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
    public ErrorInterceptors(Bugsnag bugsnag) {
        this.bugsnag = bugsnag;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {
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
        bugsnag.logAndReportToBugsnag(exception);
        final String message = Optional.ofNullable(exception.getMessage()).orElse(exception.getClass().getSimpleName());
        return new ResponseEntity(new RestControllerErrorResponse(message), httpStatus);
    }

    @ExceptionHandler(value = {Exception.class})
    public ResponseEntity unknownException(Exception e) {
        if (e instanceof BadRequestError) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } else if (e instanceof ResourceNotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } else if (e instanceof AvniNoUserSessionException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } else if (e instanceof AvniAccessException) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } else if (e instanceof InvalidFormatException || e instanceof HttpMessageConversionException) {
            return error(e, HttpStatus.BAD_REQUEST);
        } else {
            bugsnag.logAndReportToBugsnag(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers, HttpStatus status, WebRequest request) {
        bugsnag.logAndReportToBugsnag(ex);
        return super.handleExceptionInternal(ex, body, headers, status, request);
    }
}
