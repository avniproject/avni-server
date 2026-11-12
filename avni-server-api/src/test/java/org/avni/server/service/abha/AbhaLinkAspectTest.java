package org.avni.server.service.abha;

import org.avni.server.domain.Individual;
import org.avni.server.service.OrganisationConfigService;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// A subject save keeps the subject's ABHA link, then tells the listeners whether that changed it, only with ABHA on.
public class AbhaLinkAspectTest {
    private final OrganisationConfigService organisationConfigService = mock(OrganisationConfigService.class);
    private final AbhaLinkService abhaLinkService = mock(AbhaLinkService.class);
    private final AbhaLinkListener listener = mock(AbhaLinkListener.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<AbhaLinkListener> listeners = mock(ObjectProvider.class);
    private final AbhaLinkAspect aspect = new AbhaLinkAspect(organisationConfigService, abhaLinkService, listeners);

    @Test
    public void withAbhaOnTheListenersAreToldWhetherTheSaveChangedTheLink() {
        when(organisationConfigService.isAbhaEnabled()).thenReturn(true);
        when(listeners.orderedStream()).thenAnswer(inv -> Stream.of(listener));
        Individual changed = new Individual();
        Individual unchanged = new Individual();
        when(abhaLinkService.syncLink(changed)).thenReturn(true);
        when(abhaLinkService.syncLink(unchanged)).thenReturn(false);

        aspect.syncLink(changed);
        aspect.syncLink(unchanged);

        verify(listener).abhaLinkKept(changed, true);
        verify(listener).abhaLinkKept(unchanged, false);
    }

    @Test
    public void withAbhaOffNothingIsKeptAndNoListenerIsTold() {
        when(organisationConfigService.isAbhaEnabled()).thenReturn(false);

        aspect.syncLink(new Individual());

        verify(abhaLinkService, never()).syncLink(any());
        verifyNoInteractions(listeners, listener);
    }

    @Test
    public void noSubjectIsNothing() {
        aspect.syncLink(null);

        verifyNoInteractions(organisationConfigService, abhaLinkService, listeners);
        verify(listener, never()).abhaLinkKept(any(), anyBoolean());
    }
}
