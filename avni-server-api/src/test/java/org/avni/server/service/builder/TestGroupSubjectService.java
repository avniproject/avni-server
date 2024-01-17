package org.avni.server.service.builder;

import org.avni.server.dao.GroupSubjectRepository;
import org.avni.server.domain.GroupSubject;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.domain.ValidationException;
import org.avni.server.service.GroupSubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestGroupSubjectService {
    private final GroupSubjectService groupSubjectService;
    private final GroupSubjectRepository groupSubjectRepository;

    @Autowired
    public TestGroupSubjectService(GroupSubjectService groupSubjectService, GroupSubjectRepository groupSubjectRepository) {
        this.groupSubjectService = groupSubjectService;
        this.groupSubjectRepository = groupSubjectRepository;
    }

    public GroupSubject save(GroupSubject groupSubject) throws ValidationException {
        groupSubject.setMemberSubjectAddressId(groupSubject.getMemberSubject().getAddressLevel().getId());
        groupSubject.setGroupSubjectAddressId(groupSubject.getGroupSubject().getAddressLevel().getId());
        return groupSubjectService.save(groupSubject);
    }

    public GroupSubject reload(GroupSubject groupSubject) {
        return groupSubjectRepository.findOne(groupSubject.getId());
    }
}
