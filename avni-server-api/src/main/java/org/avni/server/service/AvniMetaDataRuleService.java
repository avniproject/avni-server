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

    /**
     * **IMPORTANT** : The response of this method only indicates whether the SubjectType is ** capable ** of being configured for Direct Assignment.
     * It **does not** indicate whether the SubjectType has been configured by the organisation administrators to be directly assignable.
     *
     * @param subjectType
     * @return Boolean value indicating whether the SubjectType is capable of being configured for Direct Assignment.
     */
    public boolean isDirectAssignmentAllowedFor(SubjectType subjectType) {
        return true;
//        if (subjectType.isGroup()) return true;
//
//        List<GroupRole> groupRolesSubjectTypeIsPartOf = groupRoleRepository.findByMemberSubjectTypeAndIsVoidedFalse(subjectType);
//        return groupRolesSubjectTypeIsPartOf.stream().noneMatch(groupRole -> groupRole.getGroupSubjectType().isDirectlyAssignable());
    }
}
