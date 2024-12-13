package org.avni.server.web.rules;

import jakarta.transaction.Transactional;
import org.avni.server.dao.RuleFailureTelemetryRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.RuleFailureTelemetry;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.RestControllerResourceProcessor;
import org.avni.server.web.request.RuleFailureTelemetryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuleFailureTelemetryController implements RestControllerResourceProcessor<RuleFailureTelemetry> {
    private final RuleFailureTelemetryRepository ruleFailureTelemetryRepository;

    @Autowired
    public RuleFailureTelemetryController(RuleFailureTelemetryRepository ruleFailureTelemetryRepository) {
        this.ruleFailureTelemetryRepository = ruleFailureTelemetryRepository;
    }

    @RequestMapping(value = "ruleFailureTelemetry", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<RuleFailureTelemetry>> getEmpty(Pageable pageable) {
        return empty(pageable);
    }

    @RequestMapping(value = "/ruleFailureTelemetry", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public void save(@RequestBody RuleFailureTelemetryRequest request) {
        User user = UserContextHolder.getUserContext().getUser();
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        RuleFailureTelemetry ruleFailureTelemetry = new RuleFailureTelemetry();
        ruleFailureTelemetry.setUser(user);
        ruleFailureTelemetry.setOrganisationId(organisation.getId());
        ruleFailureTelemetry.setErrorMessage(request.getErrorMessage());
        ruleFailureTelemetry.setIndividualUuid(request.getIndividualUuid());
        ruleFailureTelemetry.setStacktrace(request.getStacktrace());
        ruleFailureTelemetry.setRuleUuid(request.getRuleUuid());
        ruleFailureTelemetry.setErrorDateTime(request.getErrorDateTime());
        ruleFailureTelemetry.setSourceType(request.getSourceType());
        ruleFailureTelemetry.setSourceId(request.getSourceId());
        ruleFailureTelemetry.setEntityType(request.getEntityType());
        ruleFailureTelemetry.setEntityId(request.getEntityId());
        ruleFailureTelemetry.setAppType(request.getAppType());
        ruleFailureTelemetry.setClosed(request.getClosed());
        ruleFailureTelemetryRepository.save(ruleFailureTelemetry);
    }
}
