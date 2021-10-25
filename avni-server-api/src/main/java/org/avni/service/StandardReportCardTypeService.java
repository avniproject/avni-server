package org.avni.service;

import org.joda.time.DateTime;
import org.avni.dao.StandardReportCardTypeRepository;
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