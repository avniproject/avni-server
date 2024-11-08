package org.avni.server.service.sync;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.ResetSync;
import org.avni.server.service.ResetSyncService;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.assertEquals;

@Sql({"/test-data.sql"})
public class ResetSyncServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private ResetSyncService resetSyncService;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private UserRepository userRepository;

    @Before
    public void initialize() throws Exception {
        super.setUp();
        super.setUser(userRepository.findByUuid("8fecc62f-4b6b-4dd4-a27d-fa2587e59d04"));
    }

    @Test
    public void shouldCreateResetSyncRecordForUserWhenCatchmentChangesDueToLocationParentChange() {
        AddressLevel al = locationRepository.findByTitleIgnoreCase("GP1.Parent1");
        Long oldParentId = al.getParentId();
        AddressLevel newParent = locationRepository.findByTitleIgnoreCase("GP2");
        al.setParent(newParent);
        resetSyncService.recordLocationParentChange(al, oldParentId);
        Page<ResetSync> resetSyncRecordsForAffectedUser = resetSyncService.getByLastModifiedForUser(DateTime.parse("2000-01-01").toDateTime(),
            DateTime.now(),
            userRepository.findByUsername("user-reset-sync-test1@demo"),
            PageRequest.of(0, 1));
        assertEquals (1, resetSyncRecordsForAffectedUser.getContent().size());
    }

    @Test
    public void shouldNotCreateResetSyncRecordIfThereAreNoSubjectsInTheChangedLocation() {
        AddressLevel al = locationRepository.findByTitleIgnoreCase("GP2.Parent2");
        Long oldParentId = al.getParentId();
        AddressLevel newParent = locationRepository.findByTitleIgnoreCase("GP1");
        al.setParent(newParent);
        resetSyncService.recordLocationParentChange(al, oldParentId);
        Page<ResetSync> resetSyncRecordsForAffectedUser = resetSyncService.getByLastModifiedForUser(DateTime.parse("2000-01-01").toDateTime(),
            DateTime.now(),
            userRepository.findByUsername("user-reset-sync-test2@demo"),
            PageRequest.of(0, 1));
        assertEquals (0, resetSyncRecordsForAffectedUser.getContent().size());

    }
}
