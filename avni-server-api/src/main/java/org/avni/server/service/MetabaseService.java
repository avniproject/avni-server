package org.avni.server.service;

import org.avni.server.dao.metabase.CollectionRepository;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.dao.metabase.MetabaseRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MetabaseService {

    private final MetabaseRepository metabaseRepository;
    private final OrganisationService organisationService;
    private final AvniDatabase avniDatabase;
    private final String POSTGRES = "postgres";
    private DatabaseRepository databaseRepository;
    private CollectionRepository collectionRepository;

    @Autowired
    public MetabaseService(MetabaseRepository metabaseRepository, OrganisationService organisationService, AvniDatabase avniDatabase, DatabaseRepository databaseRepository, CollectionRepository collectionRepository) {
        this.metabaseRepository = metabaseRepository;
        this.organisationService = organisationService;
        this.avniDatabase = avniDatabase;
        this.databaseRepository = databaseRepository;
        this.collectionRepository = collectionRepository;
    }

    public void setupMetabase() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        String name = currentOrganisation.getName();
        String dbUser = currentOrganisation.getDbUser();

        Database database = databaseRepository.save(new Database(name, POSTGRES, new DatabaseDetails(avniDatabase, dbUser)));
        CollectionResponse metabaseCollection = collectionRepository.save(new Collection(name, name + " collection"));

        PermissionsGroup permissionsGroup = new PermissionsGroup(name);
        PermissionsGroupResponse metabasePermissionsGroup = metabaseRepository.createPermissionsGroup(permissionsGroup);

        Permissions permissions = new Permissions(metabaseRepository.getPermissionsGraph());
        metabaseRepository.assignDatabasePermissions(permissions, metabasePermissionsGroup.getId(), database.getId());

        CollectionPermissions collectionPermissions = new CollectionPermissions(metabaseRepository.getCollectionPermissionsGraph());
        metabaseRepository.updateCollectionPermissions(collectionPermissions, metabasePermissionsGroup.getId(), metabaseCollection.getId());
    }
}
