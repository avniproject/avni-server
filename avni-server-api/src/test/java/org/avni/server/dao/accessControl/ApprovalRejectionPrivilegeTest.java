package org.avni.server.dao.accessControl;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.PrivilegeRepository;
import org.avni.server.domain.PrivilegeEntityType;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The privileges added by V1_408. These are deliberately NOT granted to anybody by the migration -
 * an administrator has to hand them out on the User Groups screen. V1_389 auto-granted ManageCalendars
 * to every non-admin group, which made the privilege a no-op, and V1_392 had to delete those rows.
 */
public class ApprovalRejectionPrivilegeTest extends AbstractControllerIntegrationTest {
    @Autowired
    private PrivilegeRepository privilegeRepository;

    private Privilege find(PrivilegeType privilegeType) {
        Privilege privilege = privilegeRepository.findByType(privilegeType);
        assertNotNull(String.format("No privilege row for %s - is the V1_408 migration present?", privilegeType), privilege);
        return privilege;
    }

    @Test
    public void approvalAndRejectionPrivilegesExistAsNonTransactionPrivileges() {
        assertEquals(PrivilegeEntityType.NonTransaction, find(PrivilegeType.EditApproval).getEntityType());
        assertEquals(PrivilegeEntityType.NonTransaction, find(PrivilegeType.EditRejection).getEntityType());
    }

    /**
     * Asserts the migration's own content, not the state of group_privilege. A live count would be
     * worthless here: tear-down.sql does "DELETE FROM group_privilege where 1 = 1" and 48 test
     * classes reference it, so the count is 0 whether or not the migration auto-grants, and the
     * result would depend on run order. Reading the script pins the one thing that matters and
     * cannot be defeated by another test running first.
     */
    @Test
    public void theMigrationDoesNotGrantTheNewPrivilegesToAnybody() throws IOException {
        String migration = StreamUtils.copyToString(
                new ClassPathResource("db/migration/V1_408__add_approval_rejection_form_privileges.sql").getInputStream(),
                StandardCharsets.UTF_8);
        String statements = Arrays.stream(migration.split("\\R"))
                .filter(line -> !line.trim().startsWith("--"))
                .collect(Collectors.joining("\n"))
                .toLowerCase();

        assertTrue("V1_408 must insert the privilege rows", statements.contains("insert into privilege"));
        assertFalse(
                "V1_408 must not touch group_privilege. V1_389 auto-granted ManageCalendars to every " +
                        "non-admin group, making the privilege a no-op, and V1_392 had to delete those rows.",
                statements.contains("group_privilege"));
    }

    @Test
    public void theAdminUserCanEditApprovalAndRejectionFormsWithoutAGrant() {
        assertTrue(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditApproval));
        assertTrue(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditRejection));
    }
}
