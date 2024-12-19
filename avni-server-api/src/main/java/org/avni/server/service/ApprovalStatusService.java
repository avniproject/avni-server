package org.avni.server.service;

import org.avni.server.dao.ApprovalStatusRepository;
import org.avni.server.util.DateTimeUtil;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ApprovalStatusService implements NonScopeAwareService {

    private final ApprovalStatusRepository approvalStatusRepository;
    @Autowired
    public ApprovalStatusService(ApprovalStatusRepository approvalStatusRepository) {
        this.approvalStatusRepository = approvalStatusRepository;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return approvalStatusRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}

