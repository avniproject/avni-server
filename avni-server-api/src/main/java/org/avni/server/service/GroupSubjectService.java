package org.avni.server.service;

import org.avni.server.dao.GroupSubjectRepository;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.UserSubjectAssignmentRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GroupSubjectService implements ScopeAwareService<GroupSubject> {
    private final GroupSubjectRepository groupSubjectRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final UserSubjectAssignmentRepository userSubjectAssignmentRepository;

    @Autowired
    public GroupSubjectService(GroupSubjectRepository groupSubjectRepository, SubjectTypeRepository subjectTypeRepository, UserSubjectAssignmentRepository userSubjectAssignmentRepository) {
        this.groupSubjectRepository = groupSubjectRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.userSubjectAssignmentRepository = userSubjectAssignmentRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String groupSubjectTypeUuid) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(groupSubjectTypeUuid);
        User user = UserContextHolder.getUserContext().getUser();
        return subjectType != null && isChangedBySubjectTypeRegistrationLocationType(user, lastModifiedDateTime, subjectType.getId(), subjectType, SyncEntityName.GroupSubject);
    }

    @Override
    public OperatingIndividualScopeAwareRepository<GroupSubject> repository() {
        return groupSubjectRepository;
    }

    public GroupSubject save(GroupSubject groupSubject) throws ValidationException {
        this.addSyncAttributes(groupSubject);
        assignMemberToTheAssigneeOfGroup(groupSubject);
        return groupSubjectRepository.save(groupSubject);
    }

    private void assignMemberToTheAssigneeOfGroup(GroupSubject groupSubject) {
        if (groupSubject.getGroupSubject().getSubjectType().isDirectlyAssignable()) {
            List<UserSubjectAssignment> groupAssignments = userSubjectAssignmentRepository.findAllBySubjectAndIsVoidedFalse(groupSubject.getGroupSubject());
            List<UserSubjectAssignment> memberAssignments = userSubjectAssignmentRepository.findAllBySubject(groupSubject.getMemberSubject());
            for (UserSubjectAssignment groupAssignment : groupAssignments) {
                User userAssignedToGroup = groupAssignment.getUser();
                UserSubjectAssignment memberAssignment = memberAssignments.stream().filter(x -> x.getUser().equals(userAssignedToGroup)).findFirst().orElse(null);
                if (memberAssignment == null) {
                    memberAssignment = UserSubjectAssignment.createNew(userAssignedToGroup, groupSubject.getMemberSubject());
                } else {
                    memberAssignment.setVoided(false);
                }
                userSubjectAssignmentRepository.save(memberAssignment);
            }
        }
    }

    private void addSyncAttributes(GroupSubject groupSubject) {
        Individual groupIndividual = groupSubject.getGroupSubject();
        SubjectType subjectType = groupIndividual.getSubjectType();
        ObservationCollection observations = groupIndividual.getObservations();
        Individual memberIndividual = groupSubject.getMemberSubject();
        if (groupIndividual.getAddressLevel() != null) {
            groupSubject.setGroupSubjectAddressId(groupIndividual.getAddressLevel().getId());
        }
        if (memberIndividual.getAddressLevel() != null) {
            groupSubject.setMemberSubjectAddressId(memberIndividual.getAddressLevel().getId());
        }
        if (subjectType.getSyncRegistrationConcept1() != null) {
            groupSubject.setGroupSubjectSyncConcept1Value(observations.getStringValue(subjectType.getSyncRegistrationConcept1()));
        }
        if (subjectType.getSyncRegistrationConcept2() != null) {
            groupSubject.setGroupSubjectSyncConcept2Value(observations.getStringValue(subjectType.getSyncRegistrationConcept2()));
        }
    }
}
