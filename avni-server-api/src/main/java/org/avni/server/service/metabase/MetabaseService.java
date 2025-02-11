package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.*;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.service.OrganisationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import java.time.Duration;

@Service
@RequestScope
public class MetabaseService {
    private static final String DB_ENGINE = "postgres";

    @Value("${avni.default.org.user.db.password}")
    private String AVNI_DEFAULT_ORG_USER_DB_PASSWORD;

    private final OrganisationService organisationService;
    private final AvniDatabase avniDatabase;
    private final DatabaseRepository databaseRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final CollectionPermissionsRepository collectionPermissionsRepository;
    private final CollectionRepository collectionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;
    private final Organisation currentOrganisation;
    private final String organisationName;
    private final String organisationDbUser;

    // Following attributes are to be used within "Request" scope only
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
        this.currentOrganisation = organisationService.getCurrentOrganisation();
        this.organisationName = currentOrganisation.getName();
        this.organisationDbUser = currentOrganisation.getDbUser();
    }

    private void setupGlobalDatabase() {
        globalDatabase = databaseRepository.getDatabase(organisationName, organisationDbUser);
        if (globalDatabase == null) {
            Database newDatabase = new Database(organisationName, DB_ENGINE, new DatabaseDetails(avniDatabase, organisationDbUser, AVNI_DEFAULT_ORG_USER_DB_PASSWORD));
            globalDatabase = databaseRepository.save(newDatabase);
        }
    }

    private void setupGlobalCollection() {
        globalCollection = collectionRepository.getCollectionByName(organisationName);
        if (globalCollection == null) {
            CollectionResponse metabaseCollection = collectionRepository.save(new CreateCollectionRequest(organisationName, organisationName + " collection"));
            globalCollection = new CollectionInfoResponse(null, metabaseCollection.getId(), false);
        }
    }

    private void setupGlobalMetabaseGroup() {
        globalMetabaseGroup = groupPermissionsRepository.findGroup(organisationName);
        if (globalMetabaseGroup == null) {
            globalMetabaseGroup = groupPermissionsRepository.createGroup(organisationName, getGlobalDatabase().getId());
        }
    }

    private void setupCollectionPermissions() {
        CollectionPermissionsService collectionPermissions = new CollectionPermissionsService(
                collectionPermissionsRepository.getCollectionPermissionsGraph()
        );
        collectionPermissions.updateAndSavePermissions(collectionPermissionsRepository, getGlobalMetabaseGroup().getId(), getGlobalCollection().getIdAsInt());
    }

    private void setupGlobalDashboard() {
        globalDashboard = metabaseDashboardRepository.getDashboardByName(getGlobalCollection());
        if (globalDashboard == null) {
            Dashboard metabaseDashboard = metabaseDashboardRepository.save(new CreateDashboardRequest(null, getGlobalCollection().getIdAsInt()));
            globalDashboard = new CollectionItem(metabaseDashboard.getName(), metabaseDashboard.getId());
        }
    }

    public void setupMetabase() {
        setupGlobalDatabase();
        sleepAwhile(10);
        setupGlobalCollection();
        sleepAwhile(10);
        setupGlobalMetabaseGroup();
        sleepAwhile(10);
        setupCollectionPermissions();
        sleepAwhile(10);
        setupGlobalDashboard();
    }

    public void sleepAwhile(long secondsToSleep) {
        try {
            Thread.sleep(Duration.ofSeconds(secondsToSleep));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public Database getGlobalDatabase() {
        if (globalDatabase == null) {
            globalDatabase = databaseRepository.getDatabase(organisationName, organisationDbUser);
            if (globalDatabase == null) {
                throw new RuntimeException("Global database not found.");
            }
        }
        return globalDatabase;
    }

    public CollectionInfoResponse getGlobalCollection() {
        if (globalCollection == null) {
            globalCollection = collectionRepository.getCollectionByName(organisationName);
            if (globalCollection == null) {
                throw new RuntimeException("Global collection not found.");
            }
        }
        return globalCollection;
    }

    public CollectionItem getGlobalDashboard() {
        if (globalDashboard == null) {
            globalDashboard = metabaseDashboardRepository.getDashboardByName(getGlobalCollection());
            if (globalDashboard == null) {
                throw new RuntimeException("Global dashboard not found.");
            }
        }
        return globalDashboard;
    }

    public Group getGlobalMetabaseGroup() {
        if (globalMetabaseGroup == null) {
            globalMetabaseGroup = groupPermissionsRepository.findGroup(organisationName);
            if (globalMetabaseGroup == null) {
                throw new RuntimeException("Global group not found.");
            }
        }
        return globalMetabaseGroup;
    }
}