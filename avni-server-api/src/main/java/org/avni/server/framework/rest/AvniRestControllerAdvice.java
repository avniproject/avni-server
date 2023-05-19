package org.avni.server.framework.rest;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

@RestControllerAdvice
public class AvniRestControllerAdvice {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(AvniRestControllerAdvice.class);

    @ExceptionHandler(InvalidFormatException.class)
    public ResponseEntity<RestControllerErrorResponse> invalidFormatException(final InvalidFormatException e) {
        return error(e, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity <RestControllerErrorResponse> error(final Exception exception, final HttpStatus httpStatus) {
        logger.error("RestControllerError", exception);
        final String message = Optional.ofNullable(exception.getMessage()).orElse(exception.getClass().getSimpleName());
        return new ResponseEntity(new RestControllerErrorResponse(message), httpStatus);
    }

    @ExceptionHandler(HttpMessageConversionException.class)
    public ResponseEntity handleAllOtherErrors(HttpMessageConversionException exception) {
        return error(exception, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
