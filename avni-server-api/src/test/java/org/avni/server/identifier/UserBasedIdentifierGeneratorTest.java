package org.avni.server.identifier;

import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.User;
import org.avni.server.domain.UserSettings;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class UserBasedIdentifierGeneratorTest {

    @Test
    public void shouldGenerateIdentifiersBasedOnUserIdPrefix() {

        PrefixedUserPoolBasedIdentifierGenerator prefixedUserPoolBasedIdentifierGenerator = mock(PrefixedUserPoolBasedIdentifierGenerator.class);
        User user = new User();

        JsonObject settings = new JsonObject();
        settings.put(UserSettings.ID_PREFIX, "ABC");
        user.setSettings(settings);

        IdentifierSource identifierSource = new IdentifierSource();
        identifierSource.setMinimumBalance(3L);
        identifierSource.setBatchGenerationSize(100L);

        IdentifierGenerator identifierGenerator = new UserBasedIdentifierGenerator(prefixedUserPoolBasedIdentifierGenerator);

        identifierGenerator.generateIdentifiers(identifierSource, user, null);

        verify(prefixedUserPoolBasedIdentifierGenerator).generateIdentifiers(identifierSource, user, "ABC", null);
    }
}
