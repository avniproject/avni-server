package org.avni.server.service;

public class IDPException extends Exception{
    public IDPException(String reason) {
        super(reason);
    }

    public IDPException(String reason, Exception cause) {
        super(reason, cause);
    }
 }
