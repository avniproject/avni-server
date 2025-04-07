package org.avni.server.importer.batch.csv.creator;

import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.domain.OperationalSubjectType;
import org.avni.server.domain.SubjectType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SubjectTypeCreator {
    private final OperationalSubjectTypeRepository operationalSubjectTypeRepository;

    @Autowired
    public SubjectTypeCreator(OperationalSubjectTypeRepository operationalSubjectTypeRepository) {
        this.operationalSubjectTypeRepository = operationalSubjectTypeRepository;
    }

    public SubjectType getSubjectType(String subjectTypeValue, String header) {
        OperationalSubjectType operationalSubjectType = operationalSubjectTypeRepository.findByNameIgnoreCase(subjectTypeValue);
        if (operationalSubjectType == null) {
            throw new RuntimeException(String.format("'%s' '%s' not found", header, subjectTypeValue));
        }
        return operationalSubjectType.getSubjectType();
    }

}
