package org.avni.server.identifier;

import org.avni.server.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static org.avni.server.service.DeviceAwareService.WEB_DEVICE_ID;

@Service
@Qualifier("userBasedIdentifierGenerator")
public class UserBasedIdentifierGenerator implements IdentifierGenerator {

    private final PrefixedUserPoolBasedIdentifierGenerator prefixedUserPoolBasedIdentifierGenerator;


    @Autowired
    public UserBasedIdentifierGenerator(PrefixedUserPoolBasedIdentifierGenerator prefixedUserPoolBasedIdentifierGenerator) {
        this.prefixedUserPoolBasedIdentifierGenerator = prefixedUserPoolBasedIdentifierGenerator;
    }

    @Override
    public void generateIdentifiers(IdentifierSource identifierSource, User user, String deviceId) {
        String idPrefix = getIdPrefix(user);
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(identifierSource, user, idPrefix, deviceId);
    }

    @Override
    public IdentifierAssignment generateSingleIdentifier(IdentifierSource identifierSource, User user) {
        String idPrefix = getIdPrefix(user);
        IdentifierAssignment identifierAssignment = prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, idPrefix, WEB_DEVICE_ID);
        return identifierAssignment;
    }

    private String getIdPrefix(User user) {
        assert user.getSettings() != null;
        UserSettings userSettings = user.getUserSettings();
        String idPrefix = userSettings.getIdPrefix();
        if (idPrefix == null) {
            throw new IllegalArgumentException("Missing idPrefix setting for user " + user.getUsername());
        }
        return idPrefix;
    }
}
