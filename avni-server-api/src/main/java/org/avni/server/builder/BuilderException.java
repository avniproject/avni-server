package org.avni.server.builder;

public class BuilderException extends RuntimeException {
    private final String bundleSpecificMessage;

    public BuilderException(String message) {
        super(message);
        bundleSpecificMessage = null;
    }

    public BuilderException(String message, String bundleMessage) {
        super(message);
        this.bundleSpecificMessage = bundleMessage;
    }

    public String getUserMessage() {
        return super.getMessage();
    }

    @Override
    public String getMessage() {
        if (bundleSpecificMessage == null) {
            return super.getMessage();
        }
        return String.format("%s (%s)", super.getMessage(), bundleSpecificMessage);
    }
}
