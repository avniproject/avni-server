package org.avni.server.service.exception;

import javax.persistence.EntityNotFoundException;

public class GroupNotFoundException extends EntityNotFoundException {

    /**
     * Constructs a new <code>GroupNotFoundException</code> exception with
     * <code>null</code> as its detail message.
     */
    public GroupNotFoundException() {
        super();
    }

    /**
     * Constructs a new <code>GroupNotFoundException</code> exception with the
     * specified detail message.
     *
     * @param message
     *            the detail message.
     */
    public GroupNotFoundException(String message) {
        super(message);
    }
}
