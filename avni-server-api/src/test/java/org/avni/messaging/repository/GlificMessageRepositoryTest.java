package org.avni.messaging.repository;

import org.avni.messaging.domain.exception.GlificNotConfiguredException;
import org.avni.messaging.external.GlificRestClient;
import org.junit.Test;

import static org.mockito.Mockito.mock;

public class GlificMessageRepositoryTest {

    @Test
    public void shouldSendMessage() throws GlificNotConfiguredException {
        GlificRestClient glificRestClient = mock(GlificRestClient.class);
        new GlificMessageRepository(glificRestClient).sendMessageToContact("templateId", "1233", new String[]{"1", "2"});
    }
}
