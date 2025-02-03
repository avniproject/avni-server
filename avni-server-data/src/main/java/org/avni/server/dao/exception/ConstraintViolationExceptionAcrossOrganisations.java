package org.avni.server.service.exception;

import org.hibernate.exception.ConstraintViolationException;

public class ConstraintViolationExceptionAcrossOrganisations extends ConstraintViolationException {
    public ConstraintViolationExceptionAcrossOrganisations(String message, ConstraintViolationException cve) {
        super(message, cve.getSQLException(),  cve.getSQL(), cve.getConstraintName());
    }
}