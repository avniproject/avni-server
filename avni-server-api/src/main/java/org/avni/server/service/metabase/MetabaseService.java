package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.CollectionPermissionsRepository;
import org.avni.server.dao.metabase.CollectionRepository;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.dao.metabase.GroupPermissionsRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.metabase.*;
import org.avni.server.service.OrganisationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MetabaseService {

    @Value("${avni.org.password}")
    private String AVNI_DEFAULT_PASSWORD;
    public static final String DB_ENGINE = "postgres";
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
            Database newDatabase = new Database(name, DB_ENGINE, new DatabaseDetails(avniDatabase, dbUser, AVNI_DEFAULT_PASSWORD));
            globalDatabase = databaseRepository.save(newDatabase);
        }

        globalCollection = databaseRepository.getCollectionByName(globalDatabase);
        if (globalCollection == null) {
            CollectionResponse metabaseCollection = collectionRepository.save(new CreateCollectionRequest(name, name + " collection"));
            globalCollection = new CollectionInfoResponse(null, metabaseCollection.getId(), false);
        }

        Group metabaseGroup = groupPermissionsRepository.findOrCreateGroup(name, globalDatabase.getId(), globalCollection.getIdAsInt());

        CollectionPermissionsService collectionPermissions = new CollectionPermissionsService(
                collectionPermissionsRepository.getCollectionPermissionsGraph()
        );
        collectionPermissions.updateAndSavePermissions(collectionPermissionsRepository, metabaseGroup.getId(), globalCollection.getIdAsInt());
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

}