package org.avni.server.service;

import org.avni.server.dao.RuleDependencyRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.RuleDependency;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.web.request.RuleDependencyRequest;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RuleDependencyService implements NonScopeAwareService {

    private final RuleDependencyRepository ruleDependencyRepository;

    public void uploadRuleDependency(RuleDependencyRequest ruleDependencyRequest, Organisation organisation) {
        RuleDependency ruleDependency =  ruleDependencyRepository.findByOrganisationId(organisation.getId());
        if (ruleDependency == null) {
            ruleDependency = new RuleDependency();
            ruleDependency.assignUUIDIfRequired();
        }
        ruleDependency.setCode(ruleDependencyRequest.getCode());
        ruleDependency.setChecksum(ruleDependencyRequest.getHash());
        ruleDependencyRepository.save(ruleDependency);
    }

    @Autowired
    public RuleDependencyService(RuleDependencyRepository ruleDependencyRepository) {
        this.ruleDependencyRepository = ruleDependencyRepository;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return ruleDependencyRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}
