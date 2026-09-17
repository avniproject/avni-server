package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.domain.ApprovalStatus;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.service.EntityApprovalStatusService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * #1053 - the bulk approval contract, pinned at the writer.
 *
 * The writer is currently unreachable: nothing autowires EntityApprovalStatusWriter and nothing calls
 * saveStatus, so a CSV import creates no approval row at all today. These tests exist so that whoever
 * wires it back up inherits the two properties this story cares about, rather than rediscovering them.
 *
 * The first is the autoApprove parsing. It arrives as a String job parameter, not a boolean, and goes
 * through Boolean.parseBoolean - so anything that is not "true", ignoring case, silently means Pending.
 * That is a well-worn source of confusing import results and is worth a test that names the behaviour.
 *
 * The second is that saveStatus delegates to createStatus, whose signature carries no observations
 * argument. A bulk-imported decision therefore cannot carry form answers by construction, which is what
 * makes AC #3 true regardless of whether an approval form is attached.
 */
public class EntityApprovalStatusWriterUnitTest {
    @Mock
    private EntityApprovalStatusService entityApprovalStatusService;
    @Mock
    private FormMapping formMapping;

    private EntityApprovalStatusWriter writer;

    @Before
    public void setup() {
        initMocks(this);
        writer = new EntityApprovalStatusWriter(entityApprovalStatusService);
    }

    private void saveStatusWithAutoApprove(String autoApprove) {
        ReflectionTestUtils.setField(writer, "autoApprove", autoApprove);
        writer.saveStatus(formMapping, 42L, EntityApprovalStatus.EntityType.Subject, "subject-type-uuid");
    }

    private void assertCreatedWithStatus(ApprovalStatus.Status status) {
        verify(entityApprovalStatusService).createStatus(EntityApprovalStatus.EntityType.Subject, 42L,
                status, "subject-type-uuid", formMapping);
    }

    @Test
    public void anImportMarkedAutoApproveApprovesTheRecord() {
        saveStatusWithAutoApprove("true");

        assertCreatedWithStatus(ApprovalStatus.Status.Approved);
    }

    @Test
    public void anImportNotMarkedAutoApproveLeavesTheRecordPending() {
        saveStatusWithAutoApprove("false");

        assertCreatedWithStatus(ApprovalStatus.Status.Pending);
    }

    /**
     * JobService writes the parameter with String.valueOf, so a missing one reaches the writer as null
     * rather than absent. Pending is the safe reading and the one parseBoolean gives.
     */
    @Test
    public void aMissingAutoApproveParameterLeavesTheRecordPending() {
        saveStatusWithAutoApprove(null);

        assertCreatedWithStatus(ApprovalStatus.Status.Pending);
    }

    /**
     * Not a hypothetical: parseBoolean is exact-match after case folding, so a stray space or a "1" means
     * Pending, silently. Anyone wiring this up from a new caller needs to know that.
     */
    @Test
    public void anythingOtherThanTheWordTrueLeavesTheRecordPending() {
        saveStatusWithAutoApprove("1");

        assertCreatedWithStatus(ApprovalStatus.Status.Pending);
    }

    @Test
    public void theAutoApproveFlagIsNotCaseSensitive() {
        saveStatusWithAutoApprove("TRUE");

        assertCreatedWithStatus(ApprovalStatus.Status.Approved);
    }
}
