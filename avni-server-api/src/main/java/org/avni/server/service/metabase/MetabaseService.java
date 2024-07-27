package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.CollectionPermissionsRepository;
import org.avni.server.dao.metabase.CollectionRepository;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.dao.metabase.GroupPermissionsRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.service.OrganisationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;


@Service
public class MetabaseService {

    private final OrganisationService organisationService;
    private final AvniDatabase avniDatabase;
    private final DatabaseRepository databaseRepository;
    private final DatabaseService databaseService;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final CollectionPermissionsRepository collectionPermissionsRepository;
    private final CollectionRepository collectionRepository;
    private Database globalDatabase;
    private CollectionResponse globalCollection;

    @Autowired
    public MetabaseService(OrganisationService organisationService,
                           AvniDatabase avniDatabase,
                           DatabaseRepository databaseRepository,
                           @Lazy DatabaseService databaseService,
                           GroupPermissionsRepository groupPermissionsRepository,
                           CollectionPermissionsRepository collectionPermissionsRepository,
                           CollectionRepository collectionRepository) {
        this.organisationService = organisationService;
        this.avniDatabase = avniDatabase;
        this.databaseRepository = databaseRepository;
        this.databaseService = databaseService;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.collectionPermissionsRepository = collectionPermissionsRepository;
        this.collectionRepository = collectionRepository;
    }

    public void setupMetabase() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        String name = currentOrganisation.getName();
        String dbUser = currentOrganisation.getDbUser();

        Database database = databaseRepository.save(new Database(name, "postgres", new DatabaseDetails(avniDatabase, dbUser)));
        this.globalDatabase = database;

        CollectionResponse metabaseCollection = collectionRepository.save(new Collection(name, name + " collection"));
        this.globalCollection = metabaseCollection;

        Group metabaseGroup = groupPermissionsRepository.save(new Group(name));

        GroupPermissionsService groupPermissions = new GroupPermissionsService(groupPermissionsRepository.getPermissionsGraph());
        groupPermissions.updatePermissions(metabaseGroup.getId(), database.getId());
        groupPermissionsRepository.updatePermissionsGraph(groupPermissions, metabaseGroup.getId(), database.getId());

        CollectionPermissionsService collectionPermissions = new CollectionPermissionsService(collectionPermissionsRepository.getCollectionPermissionsGraph());
        collectionPermissions.updatePermissions(metabaseGroup.getId(), metabaseCollection.getId());
        collectionPermissionsRepository.updateCollectionPermissions(collectionPermissions, metabaseGroup.getId(), metabaseCollection.getId());
    }

    public void createQuestionsForSubjectTypes() {
        databaseService.createQuestionsForSubjectTypes();
    }

    public int getGlobalDatabaseId() {
        return globalDatabase.getId();
    }

    public int getGlobalCollectionId() {
        return globalCollection.getId();
    }
}
