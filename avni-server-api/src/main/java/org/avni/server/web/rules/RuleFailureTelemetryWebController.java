package org.avni.server.web.rules;

import org.avni.server.dao.RuleFailureTelemetryRepository;
import org.avni.server.domain.RuleFailureTelemetry;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.RestControllerResourceProcessor;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;
import java.util.List;

@RestController
public class RuleFailureTelemetryWebController implements RestControllerResourceProcessor<RuleFailureTelemetry> {
    private final RuleFailureTelemetryRepository ruleFailureTelemetryRepository;
    private final AccessControlService accessControlService;

    @Autowired
    public RuleFailureTelemetryWebController(RuleFailureTelemetryRepository ruleFailureTelemetryRepository, AccessControlService accessControlService) {
        this.ruleFailureTelemetryRepository = ruleFailureTelemetryRepository;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/web/ruleFailureTelemetry", method = RequestMethod.GET)
    public CollectionModel<EntityModel<RuleFailureTelemetry>> getByStatus(@RequestParam(value = "isClosed", required = false) Boolean isClosed,
                                                  Pageable pageable) {
        Page<RuleFailureTelemetry> ruleFailureTelemetries = isClosed != null
                ? ruleFailureTelemetryRepository.findByIsClosed(isClosed, pageable)
                : ruleFailureTelemetryRepository.findAll(pageable);
        return wrap(ruleFailureTelemetries);
    }

    @RequestMapping(value = "/web/ruleFailureTelemetry", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<List<RuleFailureTelemetry>> updateStatus(@RequestParam("ids") List<Long> ids,
                                       @RequestParam(value = "isClosed") Boolean isClosed) {
        accessControlService.checkPrivilege(PrivilegeType.EditRuleFailure);
        List<RuleFailureTelemetry> ruleFailureTelemetries = ruleFailureTelemetryRepository.findAllById(ids);
        ruleFailureTelemetries.forEach(ruleFailureTelemetry -> {
            ruleFailureTelemetry.setClosed(isClosed);
            ruleFailureTelemetry.setClosedDateTime(isClosed ? DateTime.now() : null);
        });
        ruleFailureTelemetryRepository.saveAll(ruleFailureTelemetries);
        return new ResponseEntity<>(ruleFailureTelemetries, HttpStatus.CREATED);
    }
}
