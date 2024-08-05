package org.avni.server.web;

import org.avni.server.domain.Individual;
import org.avni.server.domain.accessControl.SubjectPartitionCheckStatus;
import org.avni.server.domain.accessControl.SubjectPartitionData;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.springframework.stereotype.Component;

@Component
public class TxDataControllerHelper {
    private final AccessControlService accessControlService;

    public TxDataControllerHelper(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    public void checkSubjectAccess(Individual subject, SubjectPartitionData subjectPartitionData) {
        SubjectPartitionCheckStatus subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, subjectPartitionData);
        if (!subjectPartitionCheckStatus.isPassed()) {
            throw new BadRequestError(subjectPartitionCheckStatus.getMessage());
        }
    }

    public void checkSubjectAccess(Individual subject) {
        this.checkSubjectAccess(subject, SubjectPartitionData.create(subject));
    }
}
