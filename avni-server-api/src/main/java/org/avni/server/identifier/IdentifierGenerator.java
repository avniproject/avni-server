package org.avni.server.identifier;

import org.avni.server.domain.IdentifierAssignment;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.User;

public interface IdentifierGenerator {
    void generateIdentifiers(IdentifierSource identifierSource, User user, String deviceId);
    IdentifierAssignment generateSingleIdentifier(IdentifierSource identifierSource, User user);
}
