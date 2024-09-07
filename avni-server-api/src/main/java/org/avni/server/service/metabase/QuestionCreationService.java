package org.avni.server.service.metabase;
import org.avni.server.domain.metabase.TableDetails;

public interface QuestionCreationService {
    void createQuestionForTable(TableDetails tableDetails, TableDetails addressTableDetails, String addressField, String tableField) throws Exception;
    void createQuestionForTable(String tableName, String schema) throws Exception;
}
