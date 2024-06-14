package org.avni.server.domain.metabase;

import java.util.ArrayList;
import java.util.List;

public class Groups {
    private List<Group> groups = new ArrayList<>();

    public void addGroup(Group group) {
        groups.add(group);
    }

    public Group getGroupById(int id) {
        return groups.stream().filter(g -> g.getId() == id).findFirst().orElse(null);
    }

    public List<Group> getGroups() {
        return groups;
    }
}