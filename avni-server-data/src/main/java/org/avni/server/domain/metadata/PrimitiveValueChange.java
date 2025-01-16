package org.avni.server.domain.metadata;

public class PrimitiveValueChange {
    private final Object oldValue;
    private final Object newValue;

    public PrimitiveValueChange(Object oldValue, Object newValue) {
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getNewValue() {
        return newValue;
    }
}
