// to be completed
package org.avni.server.domain.metabase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MetabaseQueryBuilder {
    private final Database database;
    private ArrayNode joins;

    public MetabaseQueryBuilder(Database database, ArrayNode joins) {
        this.database = database;
        this.joins = joins;
    }

    public MetabaseQueryBuilder forTable(TableDetails tableDetails) {
        // code to be added
        return this;
    }


    public MetabaseQueryBuilder joinWith(TableDetails joinTable, FieldDetails originField, FieldDetails destinationField) {
        // Build the join condition and add to the joins array
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode joinCondition = objectMapper.createArrayNode();

        joinCondition.add(ConditionType.EQUAL.getOperator());
        joinCondition.add(objectMapper.createArrayNode().add("field").add(originField.getId()));
        joinCondition.add(objectMapper.createArrayNode().add("field").add(destinationField.getId()));

        joins.add(joinCondition);
        return this;
    }


    public MetabaseQuery build() {
        return new MetabaseQuery(database, joins);
    }
}
