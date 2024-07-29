package org.avni.server.service;

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

    public Group saveGroup(GroupContract groupContract, Organisation organisation) {
        Group group;
        if (groupContract.isNotEveryoneGroup()) {
            group = groupRepository.findByUuid(groupContract.getUuid());
            if (group == null) {
                group = new Group();
            }
            group.setUuid(groupContract.getUuid() == null ? UUID.randomUUID().toString() : groupContract.getUuid());
            group.setName(groupContract.getName());
        } else {
            group = groupRepository.findByNameAndOrganisationId(Group.Everyone, organisation.getId());
        }
        group.setVoided(groupContract.isVoided());
        group.setHasAllPrivileges(groupContract.isHasAllPrivileges());
        return groupRepository.save(group);
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return groupRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}
