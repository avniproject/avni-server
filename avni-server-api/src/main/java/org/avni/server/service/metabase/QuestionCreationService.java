package org.avni.server.service.metabase;

public interface QuestionCreationService {
    void createQuestionForTable(String tableName, String addressTableName, String addressField, String tableField) throws Exception;
    void createQuestionForTable(String tableName, String schema) throws Exception;
}
