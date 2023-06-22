package org.avni.server.web;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.ProgramOrganisationConfigRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramOrganisationConfig;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.programConfig.VisitScheduleConfig;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.O;
import org.avni.server.web.request.ProgramConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;

@RestController
public class ProgramConfigController {
    private final ProgramOrganisationConfigRepository programOrganisationConfigRepository;
    private final ProgramRepository programRepository;
    private final ConceptRepository conceptRepository;
    private final AccessControlService accessControlService;

    @Autowired
    public ProgramConfigController(ProgramOrganisationConfigRepository programOrganisationConfigRepository, ProgramRepository programRepository, ConceptRepository conceptRepository, AccessControlService accessControlService) {
        this.programOrganisationConfigRepository = programOrganisationConfigRepository;
        this.programRepository = programRepository;
        this.conceptRepository = conceptRepository;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/{programName}/config", method = RequestMethod.POST)
    @Transactional
    public void save(@PathVariable("programName") String programName, @RequestBody ProgramConfig programConfig) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        VisitScheduleConfig visitScheduleConfig = new VisitScheduleConfig(programConfig.getVisitSchedule());
        Program program = programRepository.findByName(StringUtils.capitalize(programName.toLowerCase()));
        ProgramOrganisationConfig existingProgramOrganisationConfig = programOrganisationConfigRepository.findByProgram(program);
        ProgramOrganisationConfig programOrganisationConfig = (ProgramOrganisationConfig)
                O.coalesce(existingProgramOrganisationConfig, new ProgramOrganisationConfig());
        programOrganisationConfig.setProgram(program);
        programOrganisationConfig.setVisitSchedule(visitScheduleConfig);
        programOrganisationConfig.assignUUIDIfRequired();
        programConfig.getAtRiskConcepts().forEach(conceptContract -> {
            Concept concept = conceptRepository.findByUuid(conceptContract.getUuid());
            programOrganisationConfig.addAtRiskConcept(concept);
        });
        programOrganisationConfigRepository.save(programOrganisationConfig);
    }

}
