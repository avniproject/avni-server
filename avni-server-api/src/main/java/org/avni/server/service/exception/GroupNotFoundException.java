package org.avni.server.service.exception;

import jakarta.persistence.*;

public class GroupNotFoundException extends EntityNotFoundException {

    public GroupNotFoundException() {
        super();
    }

    public GroupNotFoundException(String message) {
        super(message);
    }
}
