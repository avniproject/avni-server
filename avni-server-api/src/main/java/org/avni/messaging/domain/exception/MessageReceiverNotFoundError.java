package org.avni.messaging.domain.exception;

import javax.ws.rs.NotFoundException;

public class MessageReceiverNotFoundError extends NotFoundException {
    public MessageReceiverNotFoundError(String msg) {
        super(msg);
    }

    public MessageReceiverNotFoundError() {super();}
}