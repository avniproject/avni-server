package org.avni.messaging.domain.exception;

import javax.ws.rs.NotFoundException;

public class GlificContactNotFoundError extends NotFoundException {
    public GlificContactNotFoundError(String msg) {
        super(msg);
    }
}