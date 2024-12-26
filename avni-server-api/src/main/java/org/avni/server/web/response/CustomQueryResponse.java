package org.avni.server.web.response;

import java.util.*;
import java.util.stream.Collectors;

public class CustomQueryResponse {
    private final Set<String> headers;
    private final List<Collection<Object>> data;
    private final int total;

    public CustomQueryResponse(List<Map<String, Object>> queryResult) {
        Optional<Map<String, Object>> oneRow = queryResult.stream().findAny();
        this.headers = oneRow.map(Map::keySet).orElseGet(HashSet::new);
        this.data = queryResult.stream().map(Map::values).collect(Collectors.toList());
        this.total = queryResult.size();
    }

    public int getTotal() {
        return total;
    }

    public Set<String> getHeaders() {
        return headers;
    }

    public List<Collection<Object>> getData() {
        return data;
    }
}
