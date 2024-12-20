package org.avni.server.identifier;

import jakarta.transaction.Transactional;
import org.avni.server.domain.IdentifierAssignment;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static org.avni.server.service.DeviceAwareService.WEB_DEVICE_ID;


@Service
@Qualifier("userPoolBasedIdentifierGenerator")
public class UserPoolBasedIdentifierGenerator implements IdentifierGenerator {
    private final PrefixedUserPoolBasedIdentifierGenerator prefixedUserPoolBasedIdentifierGenerator;


    @Autowired
    public UserPoolBasedIdentifierGenerator(PrefixedUserPoolBasedIdentifierGenerator prefixedUserPoolBasedIdentifierGenerator) {
        this.prefixedUserPoolBasedIdentifierGenerator = prefixedUserPoolBasedIdentifierGenerator;
    }

    @Override
    @Transactional
    public void generateIdentifiers(IdentifierSource identifierSource, User user, String deviceId) {
        String prefix = identifierSource.getPrefix();
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(identifierSource, user, prefix, deviceId);
    }

    @Override
    public IdentifierAssignment generateSingleIdentifier(IdentifierSource identifierSource, User user) {
        String prefix = identifierSource.getPrefix();
        return prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, prefix, WEB_DEVICE_ID);
    }
}
