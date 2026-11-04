package org.avni.server.dao.search;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SqlQuery {
    private final String query;
    private final Map<String, Object> parameters;

    public SqlQuery(String query, Map<String, Object> parameters) {
        this.query = query;
        this.parameters = withoutNulCharacters(parameters);
    }

    private static Map<String, Object> withoutNulCharacters(Map<String, Object> parameters) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        parameters.forEach((name, value) -> cleaned.put(name, withoutNulCharacters(value)));
        return cleaned;
    }

    private static Object withoutNulCharacters(Object value) {
        if (value instanceof String text) return text.replace("\u0000", "");
        if (value instanceof List<?> list) return list.stream().map(SqlQuery::withoutNulCharacters).collect(Collectors.toList());
        if (value instanceof Collection<?> collection) return collection.stream().map(SqlQuery::withoutNulCharacters).collect(Collectors.toSet());
        return value;
    }

    public String getSql() {
        return query;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
}
