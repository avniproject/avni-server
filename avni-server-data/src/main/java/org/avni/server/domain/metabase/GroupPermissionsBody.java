package org.avni.server.domain.metabase;

import java.util.HashMap;
import java.util.Map;

public class GroupPermissionsBody {
    private Map<String, Object> body;

    public GroupPermissionsBody(String name) {
        this.body = new HashMap<>();
        this.body.put("name", name);
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setBody(Map<String, Object> body) {
        this.body = body;
    }

    @Override
    public String toString() {
        return "PermissionsGroupBody{" +
                "body=" + body +
                '}';
    }
}
