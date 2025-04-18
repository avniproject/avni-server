package org.avni.server.service.metabase;
import org.avni.server.domain.metabase.FieldDetails;
import org.avni.server.domain.metabase.TableDetails;

public interface IQuestionCreationService {
    void createQuestionForTable(TableDetails tableDetails, TableDetails addressTableDetails, FieldDetails addressFieldDetails, FieldDetails tableFieldDetails) throws Exception;

    void createQuestionForTable(String tableName) throws Exception;
}
