package org.avni.server.service;

import org.avni.server.dao.MetabaseRepository;
import org.avni.server.service.OrganisationService.OrganisationDTO;
import org.avni.server.service.accessControl.AccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetabaseService {

    private final Logger logger = LoggerFactory.getLogger(MetabaseService.class);
    private final MetabaseRepository metabaseRepository;
    private final OrganisationService organisationService;
    private final AccessControlService accessControlService;

    public MetabaseService(MetabaseRepository metabaseRepository, OrganisationService organisationService,AccessControlService accessControlService) {
        this.metabaseRepository = metabaseRepository;
        this.organisationService = organisationService;
        this.accessControlService = accessControlService;
    }

    public void setupMetabase() {
        accessControlService.assertIsSuperAdmin();
        List<OrganisationDTO> organisations = organisationService.getOrganisations();

        for (OrganisationDTO organisation : organisations) {
            String name = organisation.getName();
            String dbUser = organisation.getDbUser();

            try {
                logger.info("Setting up Metabase for organisation: {}", name);

                int databaseId = metabaseRepository.createDatabase(dbUser);
                int collectionId = metabaseRepository.createCollection(name);
                int groupId = metabaseRepository.createPermissionsGroup(name);

                metabaseRepository.assignDatabasePermissions(groupId, databaseId);
                metabaseRepository.updateCollectionPermissions(groupId, collectionId);

            } catch (Exception e) {
                logger.error("Error setting up Metabase for organisation: " + name, e);
                throw new RuntimeException("Failed to setup Metabase for organisation: " + name, e);
            }
        }
    }
}
