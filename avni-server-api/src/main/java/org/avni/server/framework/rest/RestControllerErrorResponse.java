package org.avni.server.framework.rest;

public class RestControllerErrorResponse {
    private final String errorMessage;

    public RestControllerErrorResponse(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
