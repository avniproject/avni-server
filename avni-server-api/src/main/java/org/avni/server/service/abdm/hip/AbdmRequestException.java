package org.avni.server.service.abdm.hip;

import org.springframework.http.HttpStatus;

// DEMO-ONLY abdm-hackathon: never merged.
// A web request the ABDM screens cannot act on: unknown subject, no ABHA, bad input. The message is shown to the user and
// never carries an ABHA number, address or token.
public class AbdmRequestException extends RuntimeException {
    private final HttpStatus status;

    public AbdmRequestException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static AbdmRequestException notFound(String message) {
        return new AbdmRequestException(HttpStatus.NOT_FOUND, message);
    }

    public static AbdmRequestException conflict(String message) {
        return new AbdmRequestException(HttpStatus.CONFLICT, message);
    }

    public static AbdmRequestException badRequest(String message) {
        return new AbdmRequestException(HttpStatus.BAD_REQUEST, message);
    }

    public HttpStatus getStatus() {
        return status;
    }
}
