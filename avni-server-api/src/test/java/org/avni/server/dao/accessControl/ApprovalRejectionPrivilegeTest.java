package org.avni.server.dao.accessControl;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.PrivilegeRepository;
import org.avni.server.domain.PrivilegeEntityType;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.Assert.assertEquals;
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
    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    public void approvalAndRejectionPrivilegesAreNotGrantedToAnybody() {
        Integer grants = jdbcTemplate.queryForObject(
                "select count(*) from group_privilege gp " +
                        "join privilege p on p.id = gp.privilege_id " +
                        "where p.type in ('EditApproval', 'EditRejection')", Integer.class);
        assertEquals("The migration must not auto-grant these privileges - see V1_392", Integer.valueOf(0), grants);
    }

    @Test
    public void theAdminUserCanEditApprovalAndRejectionFormsWithoutAGrant() {
        assertTrue(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditApproval));
        assertTrue(privilegeRepository.isAllowedForAdmin(PrivilegeType.EditRejection));
    }
}
