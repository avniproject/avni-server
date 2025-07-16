package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.ConceptAnswerRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.projection.CodedConceptProjection;
import org.avni.server.projection.ConceptProjection;
import org.avni.server.service.ConceptService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.S;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.application.ConceptUsageContract;
import org.avni.server.web.request.syncAttribute.ConceptSyncAttributeContract;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.query.Param;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;


@RestController
public class ConceptController implements RestControllerResourceProcessor<Concept> {
    private final Logger logger;
    private final ConceptRepository conceptRepository;
    private final ConceptService conceptService;
    private final ProjectionFactory projectionFactory;
    private final ConceptAnswerRepository conceptAnswerRepository;
    private final AccessControlService accessControlService;
    private final ErrorBodyBuilder errorBodyBuilder;

    @Autowired
    public ConceptController(ConceptRepository conceptRepository, ConceptService conceptService, ProjectionFactory projectionFactory, ConceptAnswerRepository conceptAnswerRepository, AccessControlService accessControlService, ErrorBodyBuilder errorBodyBuilder) {
        this.conceptRepository = conceptRepository;
        this.conceptService = conceptService;
        this.projectionFactory = projectionFactory;
        this.conceptAnswerRepository = conceptAnswerRepository;
        this.accessControlService = accessControlService;
        this.errorBodyBuilder = errorBodyBuilder;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/concepts", method = RequestMethod.POST)
    @Transactional
    ConceptProjection save(@RequestBody ConceptContract conceptRequest) {
        accessControlService.checkPrivilege(PrivilegeType.EditConcept);
        List<String> conceptUUIDs = conceptService.saveOrUpdateConcepts(Collections.singletonList(conceptRequest), ConceptContract.RequestType.Full);
        return this.getOneForWeb(conceptUUIDs.get(0));
    }

    // uses only in tests (tests use bundle upload type of request)
    @RequestMapping(value = "/concepts/bulk", method = RequestMethod.POST)
    @Transactional
    void save(@RequestBody List<ConceptContract> conceptRequests) {
        accessControlService.checkPrivilege(PrivilegeType.EditConcept);
        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Bundle);
    }

    @GetMapping(value = "/web/concept/{uuid}")
    @ResponseBody
    public ConceptProjection getOneForWeb(@PathVariable String uuid) {
        return projectionFactory.createProjection(ConceptProjection.class, conceptService.get(uuid));
    }

    @GetMapping(value = "/web/concept")
    @ResponseBody
    public ResponseEntity<ConceptProjection> getOneForWebByName(@RequestParam String name) {
        Concept concept = conceptRepository.findByName(HtmlUtils.htmlUnescape(name));
        if (concept == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(projectionFactory.createProjection(ConceptProjection.class, concept));
    }

    @GetMapping(value = "/web/concepts")
    @ResponseBody
    public CollectionModel<EntityModel<Concept>> getAll(@RequestParam(value = "name", required = false) String name, Pageable pageable) {
        Sort sortWithId = pageable.getSort().and(Sort.by("id"));
        PageRequest pageRequest = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortWithId);
        if (name == null) {
            return wrap(conceptRepository.getAllNonVoidedConcepts(pageRequest));
        } else {
            return wrap(conceptRepository.findByIsVoidedFalseAndNameIgnoreCaseContaining(name, pageRequest));
        }
    }

    @GetMapping(value = "/web/concept/usage/{uuid}")
    @ResponseBody
    public ResponseEntity<ConceptUsageContract> getConceptUsage(@PathVariable String uuid) {
        ConceptUsageContract conceptUsageContract = new ConceptUsageContract();
        Concept concept = conceptRepository.findByUuid(uuid);
        if (concept == null)
            return ResponseEntity.notFound().build();
        if (ConceptDataType.NA.toString().equals(concept.getDataType())) {
            conceptService.addDependentConcepts(conceptUsageContract, concept);
        } else {
            conceptService.addDependentFormDetails(conceptUsageContract, concept);
        }
        return ResponseEntity.ok(conceptUsageContract);
    }

    @GetMapping(value = "/codedConcepts")
    @ResponseBody
    public List<CodedConceptProjection> getAllCodedConcepts() {
        return conceptRepository.findAllByDataType("Coded")
                .stream()
                .map(t -> projectionFactory.createProjection(CodedConceptProjection.class, t))
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/concept/dataTypes")
    @ResponseBody
    public List<String> getDataTypes() {
        return Stream.of(ConceptDataType.values())
                .map(ConceptDataType::name)
                .collect(Collectors.toList());
    }

    @DeleteMapping(value = "/concept/{conceptUUID}")
    @Transactional
    public ResponseEntity deleteWeb(@PathVariable String conceptUUID) {
        accessControlService.checkPrivilege(PrivilegeType.EditConcept);
        try {
            Concept existingConcept = conceptRepository.findByUuid(conceptUUID);
            existingConcept.setVoided(!existingConcept.isVoided());
            existingConcept.setName(EntityUtil.getVoidedName(existingConcept.getName(), existingConcept.getId()));
            conceptRepository.save(existingConcept);
        } catch (Exception e) {
            logger.error(format("Error deleting concept: %s", conceptUUID), e);
            return ResponseEntity.badRequest().body(errorBodyBuilder.getErrorMessageBody(e));
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping(value = {"/concept/answerConcepts",  "/concept/answerConcepts/search/find"})
    public Page<ConceptSyncAttributeContract> getAnswerConcept(@RequestParam(value = "conceptUUID", required = false) String conceptUUID, Pageable pageable) {
        if(S.isEmpty(conceptUUID)) {
            return new PageImpl<>(Collections.emptyList());
        }
        Concept concept = conceptRepository.findByUuid(conceptUUID);
        Page<ConceptAnswer> conceptAnswers = conceptAnswerRepository.findByConceptAndIsVoidedFalse(concept, pageable);
        return conceptAnswers.map(ca -> ConceptSyncAttributeContract.fromConcept(ca.getAnswerConcept()));
    }

    @GetMapping(value = "/concept/answerConcepts/search/findAllById")
    @ResponseBody
    public Page<ConceptSyncAttributeContract> findByIds(@Param("ids") String[] ids, Pageable pageable) {
        return this.conceptRepository.findByUuidIn(ids, pageable).map(ConceptSyncAttributeContract::fromConcept);
    }

    @RequestMapping(value = "/web/concept/dashboardFilter/search", method = RequestMethod.GET)
    public List<ConceptContract> dashboardFilterSearch(@RequestParam String namePart) {
        List<Concept> dashboardFilterConcepts = conceptRepository.findDashboardFilterConcepts(namePart);
        return dashboardFilterConcepts.stream().map(ConceptContract::createForSearchResult).collect(Collectors.toList());
    }

    @RequestMapping(value = "/web/concept/media", method = RequestMethod.GET)
    public List<ConceptContract> getMediaConcepts() {
        List<String> mediaDataTypesNames = ConceptDataType.mediaDataTypes.stream().map(Enum::name).collect(Collectors.toList());
        List<Concept> concepts = conceptRepository.findAllByDataTypeInAndIsVoidedFalse(mediaDataTypesNames);
        return concepts.stream().map(ConceptContract::createForSearchResult).collect(Collectors.toList());
    }
}
