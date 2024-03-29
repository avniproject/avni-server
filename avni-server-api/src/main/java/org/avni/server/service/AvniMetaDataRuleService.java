package org.avni.server.service;

import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.domain.GroupRole;
import org.avni.server.domain.SubjectType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AvniMetaDataRuleService {
    private final GroupRoleRepository groupRoleRepository;

    @Autowired
    public AvniMetaDataRuleService(GroupRoleRepository groupRoleRepository) {
        this.groupRoleRepository = groupRoleRepository;
    }
}
