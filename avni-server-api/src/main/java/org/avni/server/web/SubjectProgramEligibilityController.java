package org.avni.server.web;

import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.program.SubjectProgramEligibilityRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.program.SubjectProgramEligibility;
import org.avni.server.service.ObservationService;
import org.avni.server.service.ScopeBasedSyncService;
import org.avni.server.service.UserService;
import org.avni.server.web.request.program.SubjectProgramEligibilityContract;
import org.avni.server.web.response.AvniEntityResponse;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;
import java.util.Collections;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;


@RestController
public class SubjectProgramEligibilityController extends AbstractController<SubjectProgramEligibility> implements RestControllerResourceProcessor<SubjectProgramEligibility> {

    private final SubjectProgramEligibilityRepository subjectProgramEligibilityRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ScopeBasedSyncService<SubjectProgramEligibility> scopeBasedSyncService;
    private final UserService userService;
    private final ObservationService observationService;
    private final IndividualRepository individualRepository;
    private final ProgramRepository programRepository;
    private final Logger logger;

    @Autowired
    public SubjectProgramEligibilityController(SubjectProgramEligibilityRepository subjectProgramEligibilityRepository,
                                               SubjectTypeRepository subjectTypeRepository,
                                               ScopeBasedSyncService<SubjectProgramEligibility> scopeBasedSyncService,
                                               UserService userService, ObservationService observationService,
                                               IndividualRepository individualRepository, ProgramRepository programRepository) {
        this.subjectProgramEligibilityRepository = subjectProgramEligibilityRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.scopeBasedSyncService = scopeBasedSyncService;
        this.userService = userService;
        this.observationService = observationService;
        this.individualRepository = individualRepository;
        this.programRepository = programRepository;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/subjectProgramEligibility/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public SlicedResources<EntityModel<SubjectProgramEligibility>> getSubjectProgramEligibilityByOperatingIndividualScopeAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid == null || subjectTypeUuid.isEmpty())
            return wrap(new SliceImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new SliceImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(subjectProgramEligibilityRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.SubjectProgramEligibility));
    }

    @RequestMapping(value = "/subjectProgramEligibility", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<EntityModel<SubjectProgramEligibility>> getSubjectProgramEligibilityByOperatingIndividualScope(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            @RequestParam(value = "subjectTypeUuid", required = false) String subjectTypeUuid,
            Pageable pageable) {
        if (subjectTypeUuid == null || subjectTypeUuid.isEmpty())
            return wrap(new PageImpl<>(Collections.emptyList()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return wrap(new PageImpl<>(Collections.emptyList()));
        return wrap(scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocation(subjectProgramEligibilityRepository, userService.getCurrentUser(), lastModifiedDateTime, now, subjectType.getId(), pageable, subjectType, SyncEntityName.SubjectProgramEligibility));
    }

    @RequestMapping(value = "/subjectProgramEligibility", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public AvniEntityResponse save(@RequestBody SubjectProgramEligibilityContract request) {
        logger.info(String.format("Saving SubjectProgramEligibility with uuid {%s}", request.getUuid()));
        ObservationCollection observations = observationService.createObservations(request.getObservations());
        SubjectProgramEligibility subjectProgramEligibility = newOrExistingEntity(subjectProgramEligibilityRepository, request, new SubjectProgramEligibility());
        Individual subject = individualRepository.findByUuid(request.getSubjectUUID());
        Program program = programRepository.findByUuid(request.getProgramUUID());
        subjectProgramEligibility.setSubject(subject);
        subjectProgramEligibility.setProgram(program);
        subjectProgramEligibility.setCheckDate(request.getCheckDate());
        subjectProgramEligibility.setEligible(request.isEligible());
        subjectProgramEligibility.setObservations(observations);
        SubjectProgramEligibility savedEntity = subjectProgramEligibilityRepository.save(subjectProgramEligibility);
        logger.info(String.format("Saved SubjectProgramEligibility with UUID %s", request.getUuid()));
        return new AvniEntityResponse(savedEntity);
    }

    @Override
    public EntityModel<SubjectProgramEligibility> process(EntityModel<SubjectProgramEligibility> resource) {
        SubjectProgramEligibility subjectProgramEligibility = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(subjectProgramEligibility.getSubject().getUuid(), "subjectUUID"));
        resource.add(Link.of(subjectProgramEligibility.getProgram().getUuid(), "programUUID"));
        addAuditFields(subjectProgramEligibility, resource);
        return resource;
    }

}
