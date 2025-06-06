package org.avni.server.service;


import org.avni.server.dao.ChecklistDetailRepository;
import org.avni.server.dao.ChecklistItemRepository;
import org.avni.server.dao.ChecklistRepository;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.Checklist;
import org.avni.server.domain.ChecklistDetail;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ChecklistService implements ScopeAwareService<Checklist> {
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final ChecklistDetailRepository checklistDetailRepository;

    @Autowired
    public ChecklistService(ChecklistRepository checklistRepository, ChecklistItemRepository checklistItemRepository, ChecklistDetailRepository checklistDetailRepository) {
        this.checklistRepository = checklistRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.checklistDetailRepository = checklistDetailRepository;
    }

    public Set<Checklist> findChecklistsByIndividual(Individual individual) {
        return checklistRepository.findByProgramEnrolmentIndividual(individual);
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String checklistDetailUuid) {
        ChecklistDetail checklistDetail = checklistDetailRepository.findByUuid(checklistDetailUuid);
        User user = UserContextHolder.getUserContext().getUser();
        Checklist checklist = checklistRepository.findFirstByChecklistDetail(checklistDetail);
        return checklistDetail != null &&
                checklist != null &&
                isChangedBySubjectTypeRegistrationLocationType(user, lastModifiedDateTime, checklistDetail.getId(), checklist.getProgramEnrolment().getIndividual().getSubjectType(), SyncEntityName.Checklist);
    }

    @Override
    public OperatingIndividualScopeAwareRepository<Checklist> repository() {
        return checklistRepository;
    }
}
