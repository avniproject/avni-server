package org.avni.server.service.builder;

import org.avni.server.domain.GroupSubject;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.ValidationException;
import org.avni.server.service.GroupSubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestGroupSubjectService {
    private final GroupSubjectService groupSubjectService;

    @Autowired
    public TestGroupSubjectService(GroupSubjectService groupSubjectService) {
        this.groupSubjectService = groupSubjectService;
    }

    public GroupSubject save(GroupSubject groupSubject) throws ValidationException {
        groupSubject.setMemberSubjectAddressId(groupSubject.getMemberSubject().getAddressLevel().getId());
        groupSubject.setGroupSubjectAddressId(groupSubject.getGroupSubject().getAddressLevel().getId());
        return groupSubjectService.save(groupSubject);
    }
}
