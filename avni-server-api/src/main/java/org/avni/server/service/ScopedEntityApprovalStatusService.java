package org.avni.server.service;

import org.avni.server.dao.EntityApprovalStatusRepository;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.domain.EntityApprovalStatus;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScopedEntityApprovalStatusService implements ScopeAwareService<EntityApprovalStatus> {
    private static org.slf4j.Logger logger = LoggerFactory.getLogger(ScopedEntityApprovalStatusService.class);

    private EntityApprovalStatusRepository entityApprovalStatusRepository;
    private OrganisationConfigService organisationConfigService;

    public ScopedEntityApprovalStatusService(EntityApprovalStatusRepository entityApprovalStatusRepository, OrganisationConfigService organisationConfigService) {
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.organisationConfigService = organisationConfigService;
    }

    /**
     * We are always returning true, as we do not want to increase the response time for SyncDetails API call.
     * And the ApprovalSyncStatus fetch by entityType and entityTypeUUID will ensure we fetch only newer entries required by the user.
     * @param lastModifiedDateTime
     * @param encounterTypeUuid
     * @return
     */
    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String encounterTypeUuid) {
         return true;
    }

    @Override
    public OperatingIndividualScopeAwareRepository<EntityApprovalStatus> repository() {
        return entityApprovalStatusRepository;
    }
}
