package org.avni.server.service;

import org.avni.server.common.BulkItemSaveException;
import org.avni.server.dao.GroupRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.Organisation;
import org.avni.server.web.request.GroupContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.joda.time.DateTime;
import java.util.UUID;

@Service
public class GroupsService implements NonScopeAwareService {
    private final GroupRepository groupRepository;

    @Autowired
    public GroupsService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    private Group createNewGroup(GroupContract groupContract, Organisation organisation) {
        Group group = new Group();
        group.setUuid(groupContract.getUuid() == null ? UUID.randomUUID().toString() : groupContract.getUuid());
        group.setName(groupContract.getName());
        group.setOrganisationId(organisation.getId());
        return group;
    }

    public Group saveGroup(GroupContract groupContract, Organisation organisation) {
        Group group;
        boolean isDefaultGroup = Group.Everyone.equals(groupContract.getName()) ||
                                 Group.Administrators.equals(groupContract.getName()) ||
                                 Group.METABASE_USERS.equals(groupContract.getName()) ;
        
        if (isDefaultGroup) {
            group = groupRepository.findByNameAndOrganisationId(groupContract.getName(), organisation.getId());
        } else {
            group = groupRepository.findByUuid(groupContract.getUuid());
        }
        
        if (group == null) {
            group = createNewGroup(groupContract, organisation);
        } else if (!isDefaultGroup) {
            group.setName(groupContract.getName());
        }
        
        group.setVoided(groupContract.isVoided());
        group.setHasAllPrivileges(groupContract.isHasAllPrivileges());
        return groupRepository.save(group);
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return groupRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public void saveGroups(GroupContract[] groupContracts, Organisation organisation) {
        for (GroupContract groupContract : groupContracts) {
            try {
                saveGroup(groupContract, organisation);
            } catch (Exception e) {
                throw new BulkItemSaveException(groupContract, e);
            }
        }
    }
}
