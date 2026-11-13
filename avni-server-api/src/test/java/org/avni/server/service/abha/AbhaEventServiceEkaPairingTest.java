package org.avni.server.service.abha;

import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.User;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Where Eka's own word on a record's Eka ID is taken, before the record is stored (EkaAbhaPairing). Eka is not asked yet:
// the default asks nothing and every record is stored as before.
public class AbhaEventServiceEkaPairingTest {
    private final AbhaService abhaService = mock(AbhaService.class);
    private final AbhaEventRepository repository = mock(AbhaEventRepository.class);
    private final EkaAbhaPairing pairing = mock(EkaAbhaPairing.class);
    private AbhaEventService service;

    @Before
    public void setUp() {
        User user = new User();
        user.setId(5L);
        when(abhaService.checkAccess("type-1")).thenReturn(new AbhaAccess(user, 2L, null, "HIP-1"));
        service = new AbhaEventService(abhaService, repository, mock(AbhaRemovedEventWriter.class), mock(IndividualRepository.class), pairing);
    }

    @Test
    public void aRecordEkaRefutesIsRefusedAndNotStored() {
        when(pairing.check(any(AbhaEvent.class))).thenReturn(EkaAbhaPairing.Verdict.REFUTED);

        assertThrows(AbhaEventInvalidException.class, () -> service.recordEvent(request("oid-1")));

        verify(repository, never()).save(any());
    }

    @Test
    public void aRecordNotAskedAboutIsStoredAsBefore_andOneWithNoEkaIdIsNeverAsked() {
        when(pairing.check(any(AbhaEvent.class))).thenReturn(EkaAbhaPairing.Verdict.NOT_ASKED);

        assertTrue(service.recordEvent(request("oid-1")));
        assertTrue(service.recordEvent(request(null)));

        verify(pairing, org.mockito.Mockito.times(1)).check(any(AbhaEvent.class));
        verify(repository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    public void theDefaultAsksEkaNothing() {
        assertTrue(new NotAskedEkaAbhaPairing().check(new AbhaEvent()) == EkaAbhaPairing.Verdict.NOT_ASKED);
    }

    private static AbhaEventRequest request(String ekaOid) {
        AbhaEventRequest request = new AbhaEventRequest();
        request.setUuid(java.util.UUID.randomUUID().toString());
        request.setSubjectUUID("subject-1");
        request.setSubjectTypeUUID("type-1");
        request.setAction("linked");
        request.setAbhaNumber("12345678568912");
        request.setAbhaAddress("patient@sbx");
        request.setEkaOid(ekaOid);
        return request;
    }
}
