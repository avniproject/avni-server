package org.avni.server.web;

import org.avni.server.domain.Individual;
import org.avni.server.domain.accessControl.SubjectPartitionCheckStatus;
import org.avni.server.domain.accessControl.SubjectPartitionData;
import org.avni.server.service.accessControl.AccessControlService;
import org.springframework.stereotype.Component;

@Component
public class TxDataControllerHelper {
    private final AccessControlService accessControlService;

    public TxDataControllerHelper(AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
    }

    public void checkSubjectAccess(Individual subject, SubjectPartitionData subjectPartitionData) throws TxDataPartitionAccessDeniedException {
        SubjectPartitionCheckStatus subjectPartitionCheckStatus = accessControlService.checkSubjectAccess(subject, subjectPartitionData);
        if (!subjectPartitionCheckStatus.isPassed()) {
            throw new TxDataPartitionAccessDeniedException(subjectPartitionCheckStatus.getMessage());
        }
    }

    public void checkSubjectAccess(Individual subject) throws TxDataPartitionAccessDeniedException {
        this.checkSubjectAccess(subject, SubjectPartitionData.create(subject));
    }

    public static class TxDataPartitionAccessDeniedException extends Exception {
        public TxDataPartitionAccessDeniedException(String message) {
            super(message);
        }
    }
}
