package org.avni.server.domain.accessControl;

import org.avni.server.domain.User;

public class AvniAccessException extends RuntimeException {
    private AvniAccessException(String message) {
        super(message);
    }

    public static AvniAccessException createNoPrivilegeException(PrivilegeType privilegeType) {
        return new AvniAccessException(String.format("User doesn't have privilege of type: %s", privilegeType));
    }

    public static AvniAccessException createNoPrivilegeException(PrivilegeType privilegeType, String entityUUID, Class entityType) {
        return new AvniAccessException(String.format("User doesn't have privilege of type: %s for %s with uuid: %s", privilegeType, entityType.getName(), entityUUID));
    }

    public static AvniAccessException createForNotAdmin(User user) {
        return new AvniAccessException("User is not admin");
    }
}
