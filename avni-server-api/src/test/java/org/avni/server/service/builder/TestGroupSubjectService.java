package org.avni.server.service.builder;

import org.avni.server.dao.GroupSubjectRepository;
import org.avni.server.domain.GroupSubject;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.SubjectType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestGroupSubjectService {
    private final GroupSubjectRepository groupSubjectRepository;

    @Autowired
    public TestGroupSubjectService(GroupSubjectRepository groupSubjectRepository) {
        this.groupSubjectRepository = groupSubjectRepository;
    }

    public GroupSubject save(GroupSubject groupSubject) {
        groupSubject.setMemberSubjectAddressId(groupSubject.getMemberSubjectAddressId());
        groupSubject.setGroupSubjectAddressId(groupSubject.getGroupSubjectAddressId());
        ObservationCollection observations = groupSubject.getGroupSubject().getObservations();
        if (observations != null) {
            SubjectType subjectType = groupSubject.getGroupSubject().getSubjectType();
            groupSubject.setGroupSubjectSyncConcept1Value(observations.getStringValue(subjectType.getSyncRegistrationConcept1()));
            groupSubject.setGroupSubjectSyncConcept2Value(observations.getStringValue(subjectType.getSyncRegistrationConcept2()));
        }
        return groupSubjectRepository.save(groupSubject);
    }
}
