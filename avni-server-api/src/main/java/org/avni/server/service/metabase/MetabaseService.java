package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.*;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserGroup;
import org.avni.server.domain.metabase.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.OrganisationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MetabaseService {
    private static final String DB_ENGINE = "postgres";

    @Value("${avni.default.org.user.db.password}")
    private String AVNI_DEFAULT_ORG_USER_DB_PASSWORD;

    private final OrganisationService organisationService;
    public static final String SYNC_STATUS_AWAITING = "Awaiting ManualSchemaSyncCompletion";
    private final AvniDatabase avniDatabase;
    private final MetabaseDatabaseRepository databaseRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final CollectionPermissionsRepository collectionPermissionsRepository;
    private final CollectionRepository collectionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;
    private final MetabaseGroupRepository metabaseGroupRepository;
    private final MetabaseUserRepository metabaseUserRepository;

    private static final Logger logger = LoggerFactory.getLogger(MetabaseService.class);

    private static final long MAX_WAIT_TIME_IN_SECONDS = 300;
    private static final long EACH_SLEEP_DURATION = 3;
    public static final String SYNC_STATUS_COMPLETE = "Complete";

    private boolean setupDatabase() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        if (database == null) {
            database = Database.forDatabasePayload(organisation.getName(),
                    DB_ENGINE, new DatabaseDetails(avniDatabase, organisation.getDbUser(), AVNI_DEFAULT_ORG_USER_DB_PASSWORD));
            databaseRepository.save(database);
            return true;
        }
        return false;
    }

    private void tearDownDatabase() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(currentOrganisation);
        if (database != null)
            databaseRepository.delete(database);
    }

    private void setupCollection() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        CollectionInfoResponse globalCollection = collectionRepository.getCollection(currentOrganisation);
        if (globalCollection == null) {
            collectionRepository.save(new CreateCollectionRequest(currentOrganisation.getName(), currentOrganisation.getName() + " collection"));
        }
    }

    private void tearDownMetabaseCollection() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        CollectionInfoResponse collection = collectionRepository.getCollection(currentOrganisation);
        if (collection != null)
            collectionRepository.delete(collection);
    }

    private void setupMetabaseGroup() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        Group metabaseGroup = metabaseGroupRepository.findGroup(currentOrganisation);
        if (metabaseGroup == null) {
            metabaseGroup = metabaseGroupRepository.createGroup(currentOrganisation);
        }
        Database database = databaseRepository.getDatabase(currentOrganisation);
        // Data tab, Groups sub tab
        groupPermissionsRepository.restrictGroupAccessToItsOwnDatabaseOnly(metabaseGroup.getId(), database.getId());
        // Data tab, Databases sub tab
        groupPermissionsRepository.grantOrgDatabaseAccessForOrgGroup(database, metabaseGroup);
    }

    private void tearDownMetabaseGroup() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        Group group = metabaseGroupRepository.findGroup(currentOrganisation);
        if (group != null)
            groupPermissionsRepository.delete(group);
    }

    private void setupCollectionPermissions() {
        Group group = metabaseGroupRepository.findGroup(organisationService.getCurrentOrganisation());
        CollectionInfoResponse globalcollection = collectionRepository.getCollection(organisationService.getCurrentOrganisation());
        collectionPermissionsRepository.updateCollectionPermissions(group, globalcollection);
    }

    private void setupDashboard() {
        CollectionItem dashboard = metabaseDashboardRepository.getDashboard(getGlobalCollection());
        if (dashboard == null) {
            metabaseDashboardRepository.save(new CreateDashboardRequest(null, getGlobalCollection().getIdAsInt()));
        }
    }

    public void setupMetabase() throws InterruptedException {
        Organisation organisation = organisationService.getCurrentOrganisation();
        logger.info("[{}] Setting up database", organisation.getName());
        boolean newDatabaseCreated = setupDatabase();
        if (newDatabaseCreated) {
            this.waitForInitialSyncToComplete(organisation);
        }
        logger.info("[{}] Setting up collection", organisation.getName());
        setupCollection();
        logger.info("[{}] Setting up group", organisation.getName());
        setupMetabaseGroup();
        logger.info("[{}] Setting up collection permissions", organisation.getName());
        setupCollectionPermissions();
        logger.info("[{}] Setting up dashboard", organisation.getName());
        setupDashboard();
    }

    public void tearDownMetabase() {
        tearDownMetabaseGroup();
        tearDownMetabaseCollection();
        tearDownDatabase();
    }

    private CollectionInfoResponse getGlobalCollection() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        return collectionRepository.getCollection(organisation);
    }

    public void upsertUsersOnMetabase(List<UserGroup> usersToBeAdded) {
        Group group = metabaseGroupRepository.findGroup(UserContextHolder.getOrganisation().getName());
        if (group != null) {
            List<UserGroupMemberships> userGroupMemberships = metabaseUserRepository.getUserGroupMemberships();
            for (UserGroup value : usersToBeAdded) {
                if (value.getGroupName().contains(org.avni.server.domain.Group.METABASE_USERS)
                        && !metabaseUserRepository.emailExists(value.getUser().getEmail())) {
                    String[] nameParts = value.getUser().getName().split(" ", 2);
                    String firstName = nameParts[0];
                    String lastName = (nameParts.length > 1) ? nameParts[1] : null;
                    metabaseUserRepository.save(new CreateUserRequest(firstName, lastName, value.getUser().getEmail(), userGroupMemberships));
                } else {
                    if (!metabaseUserRepository.activeUserExists(value.getUser().getEmail())) {
                        metabaseUserRepository.reactivateMetabaseUser(value.getUser().getEmail());
                    }
                    if (!metabaseUserRepository.userExistsInCurrentOrgGroup((value.getUser().getEmail()))) {
                        metabaseUserRepository.updateGroupPermissions(new UpdateUserGroupRequest(metabaseUserRepository.getUserFromEmail(value.getUser().getEmail()).getId(), group.getId()));
                    }
                }
            }
        }
    }

    public void fixDatabaseSyncSchedule() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        databaseRepository.moveDatabaseScanningToFarFuture(database);
    }

    public void waitForInitialSyncToComplete(Organisation organisation) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        logger.info("Waiting for initial metabase database sync {}", organisation.getName());
        while (true) {
            long timeSpent = System.currentTimeMillis() - startTime;
            long timeLeft = timeSpent - (MAX_WAIT_TIME_IN_SECONDS * 1000);
            if (!(timeLeft < 0)) {
                logger.info("Initial metabase database sync timed out after {} seconds", timeSpent / 1000);
                break;
            }
            SyncStatus syncStatus = this.getInitialSyncStatus();
            if (syncStatus != SyncStatus.COMPLETE) {
                Thread.sleep(EACH_SLEEP_DURATION * 2000);
                logger.info("Sync not complete after {} seconds, waiting for metabase database sync {}", timeSpent / 1000, organisation.getName());
            } else {
                break;
            }
        }
    }

    public static final String SYNC_STATUS_TIMED_OUT = "TimedOut";
    public static final String SYNC_STATUS_INCOMPLETE = "incomplete";
    private final OrganisationConfigService organisationConfigService;

    @Autowired
    public MetabaseService(OrganisationService organisationService,
                           OrganisationConfigService organisationConfigService,
                           AvniDatabase avniDatabase,
                           MetabaseDatabaseRepository databaseRepository,
                           GroupPermissionsRepository groupPermissionsRepository,
                           CollectionPermissionsRepository collectionPermissionsRepository,
                           CollectionRepository collectionRepository,
                           MetabaseDashboardRepository metabaseDashboardRepository,
                           MetabaseGroupRepository metabaseGroupRepository,
                           MetabaseUserRepository metabaseUserRepository) {
        this.organisationService = organisationService;
        this.organisationConfigService = organisationConfigService;
        this.avniDatabase = avniDatabase;
        this.databaseRepository = databaseRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.collectionPermissionsRepository = collectionPermissionsRepository;
        this.collectionRepository = collectionRepository;
        this.metabaseDashboardRepository = metabaseDashboardRepository;
        this.metabaseGroupRepository = metabaseGroupRepository;
        this.metabaseUserRepository = metabaseUserRepository;
    }

    public void waitForManualSchemaSyncToComplete(Organisation organisation) throws InterruptedException, CannedAnalyticsException {
        // Check if we're already in the middle of waiting for sync
        String currentStatus = organisationConfigService.getMetabaseSyncStatus(organisation);
        if (SYNC_STATUS_AWAITING.equals(currentStatus)) {
            logger.info("Organisation {} is already awaiting manual schema sync completion", organisation.getName());
            throw new CannedAnalyticsException("Already awaiting manual schema sync completion");
        }

        // Set status to awaiting
        organisationConfigService.setMetabaseSyncStatus(organisation, SYNC_STATUS_AWAITING);

        long startTime = System.currentTimeMillis();
        logger.info("Waiting for manual metabase database sync {}", organisation.getName());
        Database database = this.databaseRepository.getDatabase(organisation);
        boolean syncRunning;
        boolean syncCompleted = false;

        // Check initial sync status
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(database);
        String initialSyncStatus = databaseSyncStatus.getInitialSyncStatus();
        if (SYNC_STATUS_INCOMPLETE.equalsIgnoreCase(initialSyncStatus)) {
            logger.info("Initial sync status is incomplete for {}, waiting for completion", organisation.getName());
        }

        while (true) {
            long timeSpent = System.currentTimeMillis() - startTime;
            long timeLeft = (MAX_WAIT_TIME_IN_SECONDS * 1000) - timeSpent;
            if (timeLeft <= 0) {
                logger.info("Manual metabase database sync timed out after {} seconds", timeSpent / 1000);
                organisationConfigService.setMetabaseSyncStatus(organisation, SYNC_STATUS_TIMED_OUT);
                break;
            }

            try {
                // Check if sync is still running
                syncRunning = this.databaseRepository.isSyncRunning(database);

                // If sync is not running, check if initial sync status is complete
                if (!syncRunning) {
                    databaseSyncStatus = databaseRepository.getInitialSyncStatus(database);
                    initialSyncStatus = databaseSyncStatus.getInitialSyncStatus();

                    if (SYNC_STATUS_INCOMPLETE.equalsIgnoreCase(initialSyncStatus)) {
                        // Initial sync is still incomplete, continue waiting
                        syncRunning = true;
                        logger.info("Initial sync status is still incomplete for {}, continuing to wait", organisation.getName());
                    }
                }

                if (syncRunning) {
                    Thread.sleep(EACH_SLEEP_DURATION * 2000);
                    logger.info("Sync not complete after {} seconds, waiting for manual metabase database sync {}", timeSpent / 1000, organisation.getName());
                } else {
                    syncCompleted = true;
                    organisationConfigService.setMetabaseSyncStatus(organisation, SYNC_STATUS_COMPLETE);
                    logger.info("Manual metabase database sync completed for {}", organisation.getName());
                    break;
                }
            } catch (Exception e) {
                logger.error("Error checking sync status for {}: {}", organisation.getName(), e.getMessage());
                Thread.sleep(EACH_SLEEP_DURATION * 2000); // Wait before retrying
            }
        }

        if (!syncCompleted)
            throw new CannedAnalyticsException("Manual metabase database sync didn't completed in time");
    }

    public SyncStatus getInitialSyncStatus() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(database);
        String status = databaseSyncStatus.getInitialSyncStatus();
        return SyncStatus.fromString(status);
    }

    public void syncDatabase() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        databaseRepository.reSyncSchema(database);
    }
}
