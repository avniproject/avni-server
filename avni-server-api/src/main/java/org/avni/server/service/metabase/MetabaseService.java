package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.CollectionPermissionsRepository;
import org.avni.server.dao.metabase.CollectionRepository;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.dao.metabase.GroupPermissionsRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.service.OrganisationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class MetabaseService {

    private final OrganisationService organisationService;
    private final AvniDatabase avniDatabase;
    private final DatabaseRepository databaseRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final CollectionPermissionsRepository collectionPermissionsRepository;
    private final CollectionRepository collectionRepository;
    private Database globalDatabase;
    private CollectionInfoResponse globalCollection;

    @Autowired
    public MetabaseService(OrganisationService organisationService,
                           AvniDatabase avniDatabase,
                           DatabaseRepository databaseRepository,
                           GroupPermissionsRepository groupPermissionsRepository,
                           CollectionPermissionsRepository collectionPermissionsRepository,
                           CollectionRepository collectionRepository) {
        this.organisationService = organisationService;
        this.avniDatabase = avniDatabase;
        this.databaseRepository = databaseRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.collectionPermissionsRepository = collectionPermissionsRepository;
        this.collectionRepository = collectionRepository;
    }

    public void setupMetabase() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        String name = currentOrganisation.getName();
        String dbUser = currentOrganisation.getDbUser();

        globalDatabase = databaseRepository.getDatabaseByName(new Database(name));
        if (globalDatabase == null) {
            Database newDatabase = new Database(name, "postgres", new DatabaseDetails(avniDatabase, dbUser));
            globalDatabase = databaseRepository.save(newDatabase);
        }

        globalCollection = databaseRepository.getCollectionByName(globalDatabase);
        if (globalCollection == null) {
            CollectionResponse metabaseCollection = collectionRepository.save(new CreateCollectionRequest(name, name + " collection"));
            globalCollection = new CollectionInfoResponse(null, metabaseCollection.getId(), false);
        }

        Group metabaseGroup = findOrCreateGroup(name);

        GroupPermissionsService groupPermissions = new GroupPermissionsService(groupPermissionsRepository.getPermissionsGraph());
        groupPermissions.updatePermissions(metabaseGroup.getId(), globalDatabase.getId());
        groupPermissionsRepository.updatePermissionsGraph(groupPermissions, metabaseGroup.getId(), globalDatabase.getId());

        CollectionPermissionsService collectionPermissions = new CollectionPermissionsService(collectionPermissionsRepository.getCollectionPermissionsGraph());
        collectionPermissions.updatePermissions(metabaseGroup.getId(), globalCollection.getIdAsInt());
        collectionPermissionsRepository.updateCollectionPermissions(collectionPermissions, metabaseGroup.getId(), globalCollection.getIdAsInt());
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
            globalCollection = databaseRepository.getCollectionByName(new Database(currentOrganisation.getName()));
            if (globalCollection == null) {
                throw new RuntimeException("Global database not found.");
            }
        }
        return globalCollection;
    }

    private Group findOrCreateGroup(String name) {
        List<GroupPermissionResponse> existingGroups = groupPermissionsRepository.getAllGroups();
        for (GroupPermissionResponse group : existingGroups) {
            if (group.getName().equals(name)) {
                return new Group( group.getName(),group.getId());
            }
        }
        return groupPermissionsRepository.save(new Group(name));
    }

}