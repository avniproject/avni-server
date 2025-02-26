package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.*;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserGroup;
import org.avni.server.domain.metabase.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationService;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
    private final AvniDatabase avniDatabase;
    private final DatabaseRepository databaseRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final CollectionPermissionsRepository collectionPermissionsRepository;
    private final CollectionRepository collectionRepository;
    private final MetabaseDashboardRepository metabaseDashboardRepository;
    private final MetabaseGroupRepository metabaseGroupRepository;
    private final MetabaseUserRepository metabaseUserRepository;

    @Autowired
    public MetabaseService(OrganisationService organisationService,
                           AvniDatabase avniDatabase,
                           DatabaseRepository databaseRepository,
                           GroupPermissionsRepository groupPermissionsRepository,
                           CollectionPermissionsRepository collectionPermissionsRepository,
                           CollectionRepository collectionRepository,
                           MetabaseDashboardRepository metabaseDashboardRepository,
                           MetabaseGroupRepository metabaseGroupRepository,
                           MetabaseUserRepository metabaseUserRepository) {
        this.organisationService = organisationService;
        this.avniDatabase = avniDatabase;
        this.databaseRepository = databaseRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.collectionPermissionsRepository = collectionPermissionsRepository;
        this.collectionRepository = collectionRepository;
        this.metabaseDashboardRepository = metabaseDashboardRepository;
        this.metabaseGroupRepository = metabaseGroupRepository;
        this.metabaseUserRepository = metabaseUserRepository;
    }

    private void setupDatabase() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        Database globalDatabase = databaseRepository.getDatabase(currentOrganisation);
        if (globalDatabase == null) {
            Database newDatabase = new Database(currentOrganisation.getName(), DB_ENGINE, new DatabaseDetails(avniDatabase, currentOrganisation.getDbUser(), AVNI_DEFAULT_ORG_USER_DB_PASSWORD));
            databaseRepository.save(newDatabase);
        }
    }

    private void tearDownDatabase() {
        Database database = databaseRepository.getDatabase(organisationService.getCurrentOrganisation());
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
        groupPermissionsRepository.removeAllUsersPermissionToOrgDatabase(database);
        // Data tab, Databases sub tab
        groupPermissionsRepository.removeOrgDatabaseAccessForAllOtherGroups(database, metabaseGroup);
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
        CollectionItem dashboard = metabaseDashboardRepository.getDashboardByName(getGlobalCollection());
        if (dashboard == null) {
            metabaseDashboardRepository.save(new CreateDashboardRequest(null, getGlobalCollection().getIdAsInt()));
        }
    }

    public void setupMetabase() {
        setupDatabase();
        setupCollection();
        setupMetabaseGroup();
        setupCollectionPermissions();
        setupDashboard();
    }

    public void tearDownMetabase() {
        tearDownMetabaseGroup();
        tearDownMetabaseCollection();
        tearDownDatabase();
    }

    public CollectionInfoResponse getGlobalCollection() {
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
}
