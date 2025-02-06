package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.*;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.service.OrganisationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class MetabaseService {
    private static final String DB_ENGINE = "postgres";
    private static final Logger logger = LoggerFactory.getLogger(MetabaseService.class);

    @Value("${avni.default.org.user.db.password}")
    private String AVNI_DEFAULT_ORG_USER_DB_PASSWORD;

    @Value("${avni.self.service.enabled}")
    private boolean avniSelfServiceIsEnabled;

    private final OrganisationService organisationService;
    private final AvniDatabase avniDatabase;
    private final DatabaseRepository databaseRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final CollectionPermissionsRepository collectionPermissionsRepository;
    private final CollectionRepository collectionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;
    private Database globalDatabase;
    private CollectionInfoResponse globalCollection;
    private CollectionItem globalDashboard;
    private Group globalMetabaseGroup;

    @Autowired
    public MetabaseService(OrganisationService organisationService,
                           AvniDatabase avniDatabase,
                           DatabaseRepository databaseRepository,
                           GroupPermissionsRepository groupPermissionsRepository,
                           CollectionPermissionsRepository collectionPermissionsRepository,
                           CollectionRepository collectionRepository,
                           MetabaseDashboardRepository metabaseDashboardRepository) {
        this.organisationService = organisationService;
        this.avniDatabase = avniDatabase;
        this.databaseRepository = databaseRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.collectionPermissionsRepository = collectionPermissionsRepository;
        this.collectionRepository = collectionRepository;
        this.metabaseDashboardRepository = metabaseDashboardRepository;
    }

    public void setupMetabase() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        String name = currentOrganisation.getName();
        String dbUser = currentOrganisation.getDbUser();

        globalDatabase = databaseRepository.getDatabaseByName(new Database(name));
        if (globalDatabase == null) {
            Database newDatabase = new Database(name, DB_ENGINE, new DatabaseDetails(avniDatabase, dbUser, AVNI_DEFAULT_ORG_USER_DB_PASSWORD));
            globalDatabase = databaseRepository.save(newDatabase);
        }

        globalCollection = collectionRepository.getCollectionByName(globalDatabase);
        if (globalCollection == null) {
            CollectionResponse metabaseCollection = collectionRepository.save(new CreateCollectionRequest(name, name + " collection"));
            globalCollection = new CollectionInfoResponse(null, metabaseCollection.getId(), false);
        }

        globalMetabaseGroup = groupPermissionsRepository.findGroup(name);
        if (globalMetabaseGroup == null) {
            globalMetabaseGroup = groupPermissionsRepository.createGroup(name, globalDatabase.getId());
        }

        CollectionPermissionsService collectionPermissions = new CollectionPermissionsService(
                collectionPermissionsRepository.getCollectionPermissionsGraph()
        );
        collectionPermissions.updateAndSavePermissions(collectionPermissionsRepository, globalMetabaseGroup.getId(), globalCollection.getIdAsInt());

        globalDashboard = metabaseDashboardRepository.getDashboardByName(globalCollection);
        if (globalDashboard == null) {
            Dashboard metabaseDashboard = metabaseDashboardRepository.save(new CreateDashboardRequest(null, getGlobalCollection().getIdAsInt()));
            globalDashboard = new CollectionItem(metabaseDashboard.getName(),metabaseDashboard.getId());
        }
    }

    public Database getGlobalDatabase() {
        if (globalDatabase == null) {
            Organisation currentOrganisation = organisationService.getCurrentOrganisation();
            globalDatabase = databaseRepository.getDatabaseByName(new Database(currentOrganisation.getName()));
            if (globalDatabase == null) {
                throw new RuntimeException("Global database not found.");
            }
        }
        return globalDatabase;
    }


    public CollectionInfoResponse getGlobalCollection() {
        if (globalCollection == null) {
            Organisation currentOrganisation = organisationService.getCurrentOrganisation();
            globalCollection = collectionRepository.getCollectionByName(new Database(currentOrganisation.getName()));
            if (globalCollection == null) {
                throw new RuntimeException("Global database not found.");
            }
        }
        return globalCollection;
    }

    public CollectionItem getGlobalDashboard() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        if (globalDashboard == null) {
            globalDashboard = metabaseDashboardRepository.getDashboardByName(globalCollection);
            if (globalDashboard == null) {
                throw new RuntimeException("Global dashboard not found.");
            }
        }
        return globalDashboard;
    }

    public Group getGlobalMetabaseGroup() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        if (globalMetabaseGroup == null) {
            globalMetabaseGroup = groupPermissionsRepository.findGroup(currentOrganisation.getName());
            if (globalMetabaseGroup == null) {
                throw new RuntimeException("Global group not found.");
            }
        }
        return globalMetabaseGroup;
    }

    public boolean checkIfSelfServiceIsEnabled(boolean ifNotEnabledThrowException) {
        if (ifNotEnabledThrowException && !avniSelfServiceIsEnabled) {
            logger.debug("Self-service reporting is disabled.");
            throw new HttpClientErrorException(HttpStatus.FAILED_DEPENDENCY);
        }
        return avniSelfServiceIsEnabled;
    }

}