package org.avni.server.common;

public class BulkItemSaveException extends RuntimeException {
    public BulkItemSaveException(Object contract, Exception exception) {
        super(String.format("%s. %s.", exception.getMessage(), contract.toString()), exception);
    }
}
