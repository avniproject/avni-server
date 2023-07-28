package org.avni.server.service.exception;

import javax.persistence.EntityNotFoundException;

public class GroupNotFoundException extends EntityNotFoundException {

    public GroupNotFoundException() {
        super();
    }

    public GroupNotFoundException(String message) {
        super(message);
    }
}
