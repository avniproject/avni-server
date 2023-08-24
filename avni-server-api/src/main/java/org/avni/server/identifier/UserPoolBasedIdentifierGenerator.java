package org.avni.server.identifier;

import org.avni.server.domain.IdentifierAssignment;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;


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
    public void generateIdentifiers(IdentifierSource identifierSource, User user) {
        String prefix = identifierSource.getPrefix();
        prefixedUserPoolBasedIdentifierGenerator.generateIdentifiers(identifierSource, user, prefix);
    }

    @Override
    public IdentifierAssignment generateSingleIdentifier(IdentifierSource identifierSource, User user) {
        String prefix = identifierSource.getPrefix();
        return prefixedUserPoolBasedIdentifierGenerator.generateSingleIdentifier(identifierSource, user, prefix);
    }
}
