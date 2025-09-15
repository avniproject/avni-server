package org.avni.server.service.metabase;

import org.avni.server.config.SelfServiceBatchConfig;
import org.avni.server.dao.UserRepository;
import org.avni.server.dao.metabase.*;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserGroup;
import org.avni.server.domain.metabase.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationService;
import org.avni.server.util.SyncTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static org.avni.server.domain.Group.METABASE_USERS;

@Service
public class MetabaseService {
    private static final String DB_ENGINE = "postgres";
    private final MetabaseDatabaseRepository metabaseDatabaseRepository;
    private final SelfServiceBatchConfig selfServiceBatchConfig;

    @Value("${avni.default.org.user.db.password}")
    private String avniDefaultOrgUserDbPassword;

    private final OrganisationService organisationService;
    private final AvniDatabase avniDatabase;
    private final MetabaseDatabaseRepository databaseRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final CollectionPermissionsRepository collectionPermissionsRepository;
    private final CollectionRepository collectionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;
    private final MetabaseGroupRepository metabaseGroupRepository;
    private final MetabaseUserRepository metabaseUserRepository;
    private final UserRepository userRepository;

    private static final Logger logger = LoggerFactory.getLogger(MetabaseService.class);
    private static final long EACH_SLEEP_DURATION = 3;

    @Autowired
    public MetabaseService(OrganisationService organisationService,
                           AvniDatabase avniDatabase,
                           MetabaseDatabaseRepository databaseRepository,
                           GroupPermissionsRepository groupPermissionsRepository,
                           CollectionPermissionsRepository collectionPermissionsRepository,
                           CollectionRepository collectionRepository,
                           MetabaseDashboardRepository metabaseDashboardRepository,
                           MetabaseGroupRepository metabaseGroupRepository,
                           MetabaseUserRepository metabaseUserRepository, MetabaseDatabaseRepository metabaseDatabaseRepository,
                           SelfServiceBatchConfig selfServiceBatchConfig, UserRepository userRepository) {
        this.organisationService = organisationService;
        this.avniDatabase = avniDatabase;
        this.databaseRepository = databaseRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.collectionPermissionsRepository = collectionPermissionsRepository;
        this.collectionRepository = collectionRepository;
        this.metabaseDashboardRepository = metabaseDashboardRepository;
        this.metabaseGroupRepository = metabaseGroupRepository;
        this.metabaseUserRepository = metabaseUserRepository;
        this.metabaseDatabaseRepository = metabaseDatabaseRepository;
        this.selfServiceBatchConfig = selfServiceBatchConfig;
        this.userRepository = userRepository;
    }

    private boolean setupDatabase() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        if (database != null) return false;

        database = Database.forDatabasePayload(organisation.getName(),
                DB_ENGINE, new DatabaseDetails(avniDatabase, organisation.getDbUser(), avniDefaultOrgUserDbPassword));
        databaseRepository.save(database);
        return true;
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
        boolean databaseSetup = setupDatabase();
        if (!databaseSetup) {
            logger.info("[{}] Database previously setup so possible ETL has new schema entities", organisation.getName());
            this.syncDatabase();
        }

        Database database = databaseRepository.getDatabase(organisation);
        this.waitForDatabaseSyncToComplete(organisation, database);
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

    public void upsertUsersOnMetabase(List<UserGroup> userGroups) {
        Group group = metabaseGroupRepository.findGroup(UserContextHolder.getOrganisation().getName());
        List<UserGroup> changesToMetabaseUsersGroup = userGroups.stream().filter(ug -> ug.getGroupName().equals(METABASE_USERS)).toList();
        if (group != null) {
            for (UserGroup value : changesToMetabaseUsersGroup) {
                boolean usersSharingEmailAddress = userRepository.findAllByEmailIgnoreCaseAndIsVoidedFalse(value.getUser().getEmail()).size() > 1;
                MetabaseUserData metabaseUser = metabaseUserRepository.getUserFromEmail(value.getUser().getEmail());
                if (value.isVoided()) {
                    // Email uniquely identifies a user on metabase but on avni.
                    // Remove the user from the org group on metabase only if the email id used is unique on avni as well.
                    if (!usersSharingEmailAddress) {
                        if (metabaseUser != null && metabaseUser.getGroupIds().contains(group.getId())) {
                            removeUserFromMetabaseGroup(group.getId(), metabaseUser.getId());
                        }
                    }
                } else {
                    if (metabaseUser == null) {
                        String[] nameParts = value.getUser().getName().split(" ", 2);
                        String firstName = nameParts[0];
                        String lastName = (nameParts.length > 1) ? nameParts[1] : null;
                        List<UserGroupMemberships> userGroupMemberships = metabaseUserRepository.buildDefaultUserGroupMemberships(group);
                        metabaseUserRepository.save(new CreateUserRequest(firstName, lastName, value.getUser().getEmail(), userGroupMemberships));
                    } else {
                        if (!metabaseUser.getGroupIds().contains(group.getId())) {
                            metabaseUserRepository.updateGroupPermissions(new UpdateUserGroupRequest(metabaseUser.getId(), group.getId()));
                        }
                    }
                }
            }
        }
    }

    public void removeUserFromMetabaseGroup(Integer groupId, Integer userId) {
        metabaseUserRepository.getAllMemberships().get(String.valueOf(userId)).stream().filter(metabaseGroupMembership -> metabaseGroupMembership.groupId().equals(groupId) && metabaseGroupMembership.userId().equals(userId))
                .findFirst().ifPresent(metabaseUserRepository::deleteMembership);
    }

    public void fixDatabaseSyncSchedule() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        databaseRepository.moveDatabaseScanningToFarFuture(database);
    }

    public void waitForDatabaseSyncToComplete(Organisation organisation, Database database) throws InterruptedException {
        SyncTimer timer = SyncTimer.fromMinutes(selfServiceBatchConfig.getAvniReportingMetabaseDbSyncMaxTimeoutInMinutes());
        logger.info("Waiting for initial metabase database sync {}", organisation.getName());
        timer.start();
        while (true) {
            long timeLeft = timer.getTimeLeft();
            if (timeLeft < 0) {
                logger.info("Metabase database sync timed out. {}", timer);
                break;
            }
            SyncStatus syncStatus = this.getInitialSyncStatus();
            boolean syncRunning = metabaseDatabaseRepository.isSyncRunning(database);
            if (syncStatus != SyncStatus.COMPLETE || syncRunning) {
                Thread.sleep(EACH_SLEEP_DURATION * 2000);
                logger.info("{} Metabase database sync not complete in allotted time. {}", organisation.getName(), timer);
            } else {
                logger.info("Metabase database sync completed. {}", timer);
                break;
            }
        }
    }

    public SyncStatus getInitialSyncStatus() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        DatabaseSyncStatus databaseSyncStatus = databaseRepository.getInitialSyncStatus(database);
        String status = databaseSyncStatus.getInitialSyncStatus();
        return SyncStatus.fromString(status);
    }

    public Database syncDatabase() {
        Organisation organisation = organisationService.getCurrentOrganisation();
        Database database = databaseRepository.getDatabase(organisation);
        databaseRepository.reSyncSchema(database);
        return database;
    }

    public List<String> getResourcesPresent() {
        List<String> metabaseResources = new ArrayList<>();
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        Group group = metabaseGroupRepository.findGroup(currentOrganisation);
        if (group != null) {
            metabaseResources.add(MetabaseResource.UserGroup.name());
        }
        CollectionInfoResponse collection = collectionRepository.getCollection(currentOrganisation);
        if (collection != null)
            metabaseResources.add(MetabaseResource.Collection.name());
        Database database = databaseRepository.getDatabase(currentOrganisation);
        if (database != null) {
            DatabaseDetails details = database.getDetails();
            metabaseResources.add(String.format("%s - %s - %s", MetabaseResource.Database, details.getHost(), details.getDbname()));
        }
        return metabaseResources;
    }
}
