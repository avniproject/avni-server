package org.avni.server.service;

import org.avni.server.dao.MetabaseRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.Collection;
import org.avni.server.domain.metabase.CollectionPermissions;
import org.avni.server.domain.metabase.Database;
import org.avni.server.domain.metabase.DatabaseDetails;
import org.avni.server.domain.metabase.Permissions;
import org.avni.server.domain.metabase.PermissionsGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MetabaseService {

    private final MetabaseRepository metabaseRepository;
    private final OrganisationService organisationService;

    @Value("${database.host}")
    private String dbHost;

    @Value("${database.port}")
    private String dbPort;

    @Value("${database.name}")
    private String dbName;

    @Value("${database.engine}")
    private String dbEngine;

    public MetabaseService(MetabaseRepository metabaseRepository, OrganisationService organisationService) {
        this.metabaseRepository = metabaseRepository;
        this.organisationService = organisationService;
    }

    public void setupMetabase() {
        Organisation currentOrganisation = organisationService.getCurrentOrganisation();
        String name = currentOrganisation.getName();
        String dbUser = currentOrganisation.getDbUser();

        DatabaseDetails databaseDetails = new DatabaseDetails(dbHost, dbPort, dbName, dbUser);
        Database database = new Database(name, dbEngine, databaseDetails);
        Collection collection = new Collection(name, name + " collection");
        PermissionsGroup permissionsGroup = new PermissionsGroup(name);

        int databaseId = metabaseRepository.createDatabase(database);
        int collectionId = metabaseRepository.createCollection(collection);
        int groupId = metabaseRepository.createPermissionsGroup(permissionsGroup);

        Permissions permissions = new Permissions(metabaseRepository.getPermissionsGraph());
        metabaseRepository.assignDatabasePermissions(permissions, groupId, databaseId);

        CollectionPermissions collectionPermissions = new CollectionPermissions(metabaseRepository.getCollectionPermissionsGraph());
        metabaseRepository.updateCollectionPermissions(collectionPermissions, groupId, collectionId);
    }
}
