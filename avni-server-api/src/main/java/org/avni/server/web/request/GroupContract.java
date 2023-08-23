package org.avni.server.web.request;

import org.avni.server.domain.Group;
import org.springframework.hateoas.core.Relation;

@Relation(collectionRelation = "group")
public class GroupContract extends ReferenceDataContract {

    private boolean hasAllPrivileges;

    private boolean isNotEveryoneGroup = true;

    public static GroupContract fromEntity(Group group) {
        GroupContract groupContract = new GroupContract();
        groupContract.setId(group.getId());
        groupContract.setName(group.getName());
        groupContract.setHasAllPrivileges(group.isHasAllPrivileges());
        groupContract.setNotEveryoneGroup(!group.isEveryone());
        groupContract.setUuid(group.getUuid());
        return groupContract;
    }

    public boolean isHasAllPrivileges() {
        return hasAllPrivileges;
    }

    public void setHasAllPrivileges(boolean hasAllPrivileges) {
        this.hasAllPrivileges = hasAllPrivileges;
    }

    public boolean isNotEveryoneGroup() {
        return isNotEveryoneGroup;
    }

    public void setNotEveryoneGroup(boolean notEveryoneGroup) {
        isNotEveryoneGroup = notEveryoneGroup;
    }

}
