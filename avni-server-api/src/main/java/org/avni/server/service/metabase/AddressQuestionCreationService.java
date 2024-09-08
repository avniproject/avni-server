package org.avni.server.service.metabase;

import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class AddressQuestionCreationService implements QuestionCreationService {

    private final DatabaseService databaseService;
    private final DatabaseRepository databaseRepository;

    @Autowired
    public AddressQuestionCreationService(@Lazy DatabaseService databaseService, DatabaseRepository databaseRepository) {
        this.databaseService = databaseService;
        this.databaseRepository = databaseRepository;
    }

    @Override
    public void createQuestionForTable(TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails addressFieldDetails, FieldDetails tableFieldDetails) {
        Database database = databaseService.getGlobalDatabase();
        databaseRepository.createQuestionForTable(database, tableDetails, addressTableDetails, addressFieldDetails, tableFieldDetails);
    }

    @Override
    public void createQuestionForTable(String tableName, String schema) {
        Database database = databaseService.getGlobalDatabase();

        TableDetails tableDetails = new TableDetails();
        tableDetails.setName(tableName);
        TableDetails fetchedTableDetails = databaseRepository.findTableDetailsByName(database, tableDetails);

        databaseRepository.createQuestionForIndividualTable(database, fetchedTableDetails);
    }
}
