package org.avni.server.domain.accessControl;

public class AvniAccessException extends RuntimeException {
    private AvniAccessException(String message) {
        super(message);
    }

    public static AvniAccessException createNoUserException() {
        return new AvniAccessException("User not logged in");
    }

    public static AvniAccessException createNoPrivilegeException(PrivilegeType privilegeType) {
        return new AvniAccessException(String.format("User doesn't have privilege of type: %s", privilegeType));
    }
}
