package org.avni.server.service;

import org.avni.server.dao.StandardReportCardTypeRepository;
import org.avni.server.util.DateTimeUtil;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StandardReportCardTypeService implements NonScopeAwareService {

    private final StandardReportCardTypeRepository standardReportCardTypeRepository;

    @Autowired
    public StandardReportCardTypeService(StandardReportCardTypeRepository standardReportCardTypeRepository) {
        this.standardReportCardTypeRepository = standardReportCardTypeRepository;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return standardReportCardTypeRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}
