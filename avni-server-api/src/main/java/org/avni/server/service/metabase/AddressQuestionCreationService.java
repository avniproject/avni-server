package org.avni.server.service.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
    public void createQuestionForTable(String tableName, String addressTableName, String addressField, String tableField) throws Exception {
        Database database = databaseService.getGlobalDatabase();
        TableDetails tableDetails = databaseRepository.getTableDetailsByName(database, tableName);

        databaseRepository.createQuestionForTable(database, tableDetails, addressTableName, addressField, tableField);
    }

    @Override
    public void createQuestionForTable(String tableName, String schema) throws Exception {
        Database database = databaseService.getGlobalDatabase();
        TableDetails tableDetails = databaseRepository.getTableDetailsByName(database, tableName);

        ArrayNode joinsArray = objectMapper.createArrayNode();

        MetabaseQuery query = new MetabaseQueryBuilder(database, joinsArray, objectMapper)
                .forTable(tableDetails)
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
