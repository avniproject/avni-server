package org.avni.server.service.abdm;

// A webhook event that cannot be handled yet because what it depends on has not happened (a data push before its grant).
// The inbox tries it again later, up to its cap, without raising an alarm before then.
public class AbdmNotReadyException extends RuntimeException {
    public AbdmNotReadyException(String message) {
        super(message);
    }
}
