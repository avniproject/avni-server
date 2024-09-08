package org.avni.server.service.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.dao.metabase.DatabaseRepository;
import org.avni.server.domain.metabase.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
public class AddressQuestionCreationService implements QuestionCreationService {

    private final DatabaseService databaseService;
    private final DatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;
    private final String metabaseApiUrl;

    @Autowired
    public AddressQuestionCreationService(@Lazy DatabaseService databaseService, DatabaseRepository databaseRepository, ObjectMapper objectMapper, @Value("${metabase.api.url}") String metabaseApiUrl) {
        this.databaseService = databaseService;
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
        this.metabaseApiUrl = metabaseApiUrl;
    }

    @Override
    public void createQuestionForTable(TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails addressFieldDetails, FieldDetails tableFieldDetails) throws Exception {
        Database database = databaseService.getGlobalDatabase();
        databaseRepository.createQuestionForTable(database, tableDetails, addressTableDetails, addressFieldDetails, tableFieldDetails);
    }


    @Override
    public void createQuestionForTable(String tableName, String schema) throws Exception {
        Database database = databaseService.getGlobalDatabase();

        TableDetails tableDetails = new TableDetails();
        tableDetails.setName(tableName);
        TableDetails fetchedTableDetails = databaseRepository.findTableDetailsByName(database, tableDetails);

        MetabaseQuery query = new MetabaseQueryBuilder(database, objectMapper.createArrayNode(), objectMapper)
                .forTable(fetchedTableDetails)
                .build();

        MetabaseRequestBody requestBody = new MetabaseRequestBody(
                tableName,
                query,
                VisualizationType.TABLE,
                null,
                objectMapper.createObjectNode(),
                databaseService.getCollectionId(),
                CardType.QUESTION
        );
        databaseRepository.postForObject(metabaseApiUrl + "/card", requestBody.toJson(objectMapper), JsonNode.class);
    }


}
