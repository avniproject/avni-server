package org.avni.server.web;

import org.avni.server.application.Form;
import org.avni.server.application.FormType;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.OperationalProgramRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.OperationalProgram;
import org.avni.server.domain.Program;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.service.FormMappingParameterObject;
import org.avni.server.service.FormMappingService;
import org.avni.server.service.FormService;
import org.avni.server.service.ProgramService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.contract.ProgramContract;
import org.avni.server.web.request.ProgramRequest;
import org.avni.server.web.request.webapp.ProgramContractWeb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class ProgramController implements RestControllerResourceProcessor<ProgramContractWeb> {
    private final Logger logger;
    private final ProgramRepository programRepository;
    private final OperationalProgramRepository operationalProgramRepository;
    private final ProgramService programService;
    private final IndividualRepository individualRepository;
    private final FormService formService;
    private final FormMappingService formMappingService;
    private final AccessControlService accessControlService;

    @Autowired
    public ProgramController(ProgramRepository programRepository,
                             OperationalProgramRepository operationalProgramRepository,
                             ProgramService programService,
                             IndividualRepository individualRepository,
                             FormService formService,
                             FormMappingService formMappingService, AccessControlService accessControlService) {
        this.programRepository = programRepository;
        this.operationalProgramRepository = operationalProgramRepository;
        this.programService = programService;
        this.individualRepository = individualRepository;
        this.formService = formService;
        this.formMappingService = formMappingService;
        this.accessControlService = accessControlService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/programs", method = RequestMethod.POST)
    @Transactional
    public void save(@RequestBody List<ProgramRequest> programRequests) {
        accessControlService.checkPrivilege(PrivilegeType.EditProgram);
        programRequests.forEach(programService::saveProgram);
    }

    @PostMapping(value = "/web/program")
    @Transactional
    ResponseEntity saveProgramForWeb(@RequestBody ProgramContractWeb request) {
        accessControlService.checkPrivilege(PrivilegeType.EditProgram);
        Program existingProgram =
                programRepository.findByNameIgnoreCase(request.getName());
        OperationalProgram existingOperationalProgram =
                operationalProgramRepository.findByNameIgnoreCase(request.getName());
        if (existingProgram != null || existingOperationalProgram != null)
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("Program %s already exists", request.getName())));
        Program program = new Program();
        program.assignUUIDIfRequired();
        programService.updateAndSaveProgram(program, request);
        OperationalProgram operationalProgram = new OperationalProgram();
        operationalProgram.assignUUIDIfRequired();
        operationalProgram.setName(request.getName());
        operationalProgram.setProgramSubjectLabel(request.getProgramSubjectLabel());
        operationalProgram.setProgram(program);
        operationalProgramRepository.save(operationalProgram);

        saveFormsAndMapping(request, program);

        return ResponseEntity.ok(ProgramContractWeb.fromOperationalProgram(operationalProgram));
    }

    private void saveFormsAndMapping(@RequestBody ProgramContractWeb request, Program program) {
        Form enrolmentForm = formService.getOrCreateForm(request.getProgramEnrolmentFormUuid(),
                String.format("%s Enrolment", program.getName()),
                FormType.ProgramEnrolment);
        formMappingService.saveFormMapping(
                new FormMappingParameterObject(request.getSubjectTypeUuid(), program.getUuid(), null),
                enrolmentForm, request.isEnrolmentApprovalEnabled());

        Form exitForm = formService.getOrCreateForm(request.getProgramExitFormUuid(),
                String.format("%s Exit", program.getName()),
                FormType.ProgramExit);
        formMappingService.saveFormMapping(
                new FormMappingParameterObject(request.getSubjectTypeUuid(), program.getUuid(), null),
                exitForm, request.isExitApprovalEnabled());
    }

    @PutMapping(value = "/web/program/{id}")
    @Transactional
    public ResponseEntity updateProgramForWeb(@RequestBody ProgramContractWeb request,
                                              @PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditProgram);
        logger.info(String.format("Processing Operational Program update request: %s", request.toString()));
        if (request.getName().trim().equals(""))
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError("Name can not be empty"));

        OperationalProgram operationalProgram = operationalProgramRepository.findOne(id);

        if (operationalProgram == null)
            return ResponseEntity.badRequest()
                    .body(ReactAdminUtil.generateJsonError(String.format("Operational Program with id '%d' not found", id)));

        Program program = operationalProgram.getProgram();
        programService.updateAndSaveProgram(program, request);

        operationalProgram.setProgramSubjectLabel(request.getProgramSubjectLabel());
        operationalProgram.setName(request.getName());
        operationalProgramRepository.save(operationalProgram);

        saveFormsAndMapping(request, program);

        return ResponseEntity.ok(ProgramContractWeb.fromOperationalProgram(operationalProgram));
    }

    @DeleteMapping(value = "/web/program/{id}")
    @Transactional
    public ResponseEntity voidProgram(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditProgram);
        OperationalProgram operationalProgram = operationalProgramRepository.findOne(id);
        if (operationalProgram == null)
            return ResponseEntity.notFound().build();
        Program program = operationalProgram.getProgram();
        if (program == null)
            return ResponseEntity.notFound().build();

        operationalProgram.setName(EntityUtil.getVoidedName(operationalProgram.getName(), operationalProgram.getId()));
        program.setName(EntityUtil.getVoidedName(program.getName(), program.getId()));
        operationalProgram.setVoided(true);
        program.setVoided(true);
        operationalProgramRepository.save(operationalProgram);
        programRepository.save(program);

        formMappingService.voidExistingFormMappings(new FormMappingParameterObject(null, program.getUuid(), null), null);

        return ResponseEntity.ok(null);
    }

    @GetMapping(value = "/web/program")
    @ResponseBody
    public CollectionModel<EntityModel<ProgramContractWeb>> getAll(Pageable pageable) {
        return wrap(operationalProgramRepository
                .findPageByIsVoidedFalse(pageable)
                .map(ProgramContractWeb::fromOperationalProgram));
    }

    @GetMapping(value = "/web/program/v2")
    public List<ProgramContract> getAllPrograms(@RequestParam(required = false, name = "subjectType") List<String> subjectTypeUuids) {
        List<Program> allPrograms;
        if (CollectionUtils.isEmpty(subjectTypeUuids)) {
            allPrograms = programRepository.findAllByIsVoidedFalseOrderByName();
        } else {
            allPrograms = formMappingService.getAllPrograms(subjectTypeUuids);
        }

        return allPrograms.stream().map(program -> {
            ProgramContract programContract = new ProgramContract();
            programContract.setName(program.getName());
            programContract.setUuid(program.getUuid());
            return programContract;
        }).collect(Collectors.toList());
    }

    @GetMapping(value = "web/eligiblePrograms")
    @ResponseBody
    public List<ProgramContractWeb> getEligiblePrograms(@RequestParam String subjectUuid) {
        Individual individual = individualRepository.findByUuid(subjectUuid);
        if (individual == null) throw new BadRequestError("Subject %s not found", subjectUuid);
        List<Program> eligiblePrograms = programService.getEligiblePrograms(individual);
        List<OperationalProgram> operationalPrograms = new ArrayList<>();
        for (Program p : eligiblePrograms) {
            operationalPrograms.addAll(p.getOperationalPrograms());
        }
        return operationalPrograms.stream()
                .sorted(Comparator.comparing(OperationalProgram::getName))
                .map(ProgramContractWeb::fromOperationalProgram)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/programs")
    @ResponseBody
    public List<OperationalProgram> getAllPrograms() {
        return operationalProgramRepository.findAll();
    }

    @GetMapping(value = "/web/program/{id}")
    @ResponseBody
    public ResponseEntity getOne(@PathVariable("id") Long id) {
        OperationalProgram operationalProgram = operationalProgramRepository.findOne(id);
        if (operationalProgram.isVoided())
            return ResponseEntity.notFound().build();
        ProgramContractWeb programContractWeb = ProgramContractWeb.fromOperationalProgram(operationalProgram);
        return new ResponseEntity<>(programContractWeb, HttpStatus.OK);
    }
}
