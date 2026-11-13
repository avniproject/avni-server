package org.avni.server.domain.accessControl;

import org.avni.server.domain.User;

import java.util.List;
import java.util.stream.Collectors;

public class AvniAccessException extends RuntimeException {
    private AvniAccessException(String message) {
        super(message);
    }

    public static AvniAccessException createNoPrivilegeException(PrivilegeType privilegeType) {
        return new AvniAccessException(String.format("User does not have privilege of type: %s", privilegeType));
    }

    public static AvniAccessException createNoPrivilegeException(List<PrivilegeType> privilegeTypes) {
        return new AvniAccessException(String.format("User does not have privilege of type: %s", privilegeTypes.stream().map(Enum::name).collect(Collectors.joining(" or "))));
    }

    public static AvniAccessException createNoPrivilegeException(PrivilegeType privilegeType, String entityUUID, Class entityType) {
        return new AvniAccessException(String.format("User does not have privilege of type: %s for %s with uuid: %s", privilegeType, entityType.getName(), entityUUID));
    }

    public static AvniAccessException createForNotAdmin(User user) {
        return new AvniAccessException(String.format("User %s is not admin", user.getUsername()));
    }

    public static AvniAccessException createForSavedQueriesInBundle() {
        return new AvniAccessException("Saved queries (customQueries.json) cannot be uploaded in a bundle. Remove customQueries.json from the bundle and ask the Avni team to add the query.");
    }

    public static AvniAccessException createForAdmin(User user) {
        return new AvniAccessException(String.format("User %s is admin, operation disallowed", user.getUsername()));
    }

    public static AvniAccessException createForUserNotAllowedTokenGeneration(User user) {
        return new AvniAccessException(String.format("User %s is not allowed to invoke token generation api", user.getUsername()));
    }

    public static AvniAccessException createForOrganisationNotAccessible(User user, String organisationUUID) {
        return new AvniAccessException(String.format("User %s cannot act in organisation %s", user.getUsername(), organisationUUID));
    }
}
