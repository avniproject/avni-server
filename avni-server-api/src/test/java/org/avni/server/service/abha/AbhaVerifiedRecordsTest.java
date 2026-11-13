package org.avni.server.service.abha;

import com.bugsnag.Bugsnag;
import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.domain.AbhaEvent;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// One Eka ID belongs to at most one live subject of an organisation: the first whose saved ABHA claimed it.
public class AbhaVerifiedRecordsTest {
    private static final long ORG = 2L;
    private static final String SHARED = "oid-shared";
    private static final String FIRST_NUMBER = "91111122222009";
    private static final String SECOND_NUMBER = "91333344444533";

    private final AbhaEventRepository repository = mock(AbhaEventRepository.class);
    private final Bugsnag bugsnag = mock(Bugsnag.class);
    private final AbhaVerifiedRecords records = new AbhaVerifiedRecords(repository, bugsnag);
    private AbhaEvent firstClaim;
    private AbhaEvent secondClaim;

    @Before
    public void setUp() {
        firstClaim = record(3L, "subject-1", FIRST_NUMBER, "first@sbx");
        secondClaim = record(9L, "subject-2", SECOND_NUMBER, "second@sbx");
        when(repository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-1")).thenReturn(List.of(firstClaim));
        when(repository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-2")).thenReturn(List.of(secondClaim));
        when(repository.findOtherLiveSubjectsNamingEkaOid(ORG, SHARED, "subject-1"))
                .thenReturn(List.of(holder("subject-2", SECOND_NUMBER, "second@sbx")));
        when(repository.findOtherLiveSubjectsNamingEkaOid(ORG, SHARED, "subject-2"))
                .thenReturn(List.of(holder("subject-1", FIRST_NUMBER, "first@sbx")));
    }

    @Test
    public void aSecondLiveSubjectClaimingAnEkaIdIsRefusedAndReported_theFirstKeepsIt() {
        assertEquals(Optional.empty(), records.of("subject-2", SECOND_NUMBER, "second@sbx"));
        verify(bugsnag, times(1)).notify(any(IllegalStateException.class));

        assertEquals(Optional.of(firstClaim), records.of("subject-1", FIRST_NUMBER, "first@sbx"));
        verify(bugsnag, times(1)).notify(any(IllegalStateException.class));
    }

    // Each use of a second claim refuses it, but the clash goes to Bugsnag once for each pair of patients.
    @Test
    public void aSecondClaimIsReportedOncePerPairOfPatients_notOnEveryUse() {
        for (int use = 0; use < 3; use++) {
            assertEquals(Optional.empty(), records.of("subject-2", SECOND_NUMBER, "second@sbx"));
        }
        verify(bugsnag, times(1)).notify(any(IllegalStateException.class));

        AbhaEvent thirdClaim = record(12L, "subject-3", "91000000000001", "third@sbx");
        when(repository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc("subject-3")).thenReturn(List.of(thirdClaim));
        when(repository.findOtherLiveSubjectsNamingEkaOid(ORG, SHARED, "subject-3"))
                .thenReturn(List.of(holder("subject-1", FIRST_NUMBER, "first@sbx")));
        assertEquals(Optional.empty(), records.of("subject-3", "91000000000001", "third@sbx"));
        verify(bugsnag, times(2)).notify(any(IllegalStateException.class));
    }

    // The other subject's record names the Eka ID, but the ABHA saved on it now is another one: it holds no claim.
    @Test
    public void anotherSubjectWhoseSavedAbhaNoLongerAgreesHoldsNoClaim() {
        when(repository.findOtherLiveSubjectsNamingEkaOid(ORG, SHARED, "subject-2"))
                .thenReturn(List.of(holder("subject-1", "91555566666001", "someone.new@sbx")));

        assertEquals(Optional.of(secondClaim), records.of("subject-2", SECOND_NUMBER, "second@sbx"));
        verify(bugsnag, never()).notify(any(Throwable.class));
    }

    @Test
    public void aSubjectWithNoVerifiedRecordAsksNothingMore() {
        assertEquals(Optional.empty(), records.of("subject-2", "91000000000001", null));
        verify(repository, never()).findOtherLiveSubjectsNamingEkaOid(any(), any(), any());
    }

    private static AbhaEvent record(Long id, String subjectUuid, String number, String address) {
        AbhaEvent event = new AbhaEvent();
        event.setId(id);
        event.setOrganisationId(ORG);
        event.setSubjectUuid(subjectUuid);
        event.setAction("linked");
        event.setEkaOid(SHARED);
        event.setAbhaNumber(number);
        event.setAbhaAddress(address);
        return event;
    }

    private static AbhaEventRepository.SavedAbha holder(String subjectUuid, String number, String address) {
        return new AbhaEventRepository.SavedAbha() {
            public String getSubjectUuid() { return subjectUuid; }
            public String getAbhaNumber() { return number; }
            public String getAbhaAddress() { return address; }
        };
    }
}
