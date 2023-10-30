package org.avni.server.service.builder;

import org.avni.server.application.Form;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.dao.OperationalProgramRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.OperationalProgram;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.TestFormBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestProgramService {
    private final ProgramRepository programRepository;
    private final FormMappingRepository formMappingRepository;
    private final FormRepository formRepository;
    private final OperationalProgramRepository operationalProgramRepository;

    @Autowired
    public TestProgramService(ProgramRepository programRepository, FormMappingRepository formMappingRepository, FormRepository formRepository, OperationalProgramRepository operationalProgramRepository) {
        this.programRepository = programRepository;
        this.formMappingRepository = formMappingRepository;
        this.formRepository = formRepository;
        this.operationalProgramRepository = operationalProgramRepository;
    }

    public Program addProgram(Program program, SubjectType subjectType) {
        return this.addProgramAndGetFormMapping(program, subjectType).getProgram();
    }

    public FormMapping addProgramAndGetFormMapping(Program program, SubjectType subjectType) {
        Form form = new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(FormType.ProgramEnrolment).build();
        OperationalProgram operationalProgram = new OperationalProgram();
        operationalProgram.setName(subjectType.getName());
        operationalProgram.setProgram(program);
        operationalProgram.setUuid(UUID.randomUUID().toString());
        programRepository.save(program);
        operationalProgramRepository.save(operationalProgram);
        formRepository.save(form);
        return formMappingRepository.saveFormMapping(new FormMappingBuilder().withForm(form).withProgram(program).withSubjectType(subjectType).build());
    }

    public FormMapping addProgramExitMapping(Program program, SubjectType subjectType) {
        Form form = new TestFormBuilder().withDefaultFieldsForNewEntity().withFormType(FormType.ProgramExit).build();
        formRepository.save(form);
        return formMappingRepository.saveFormMapping(new FormMappingBuilder().withForm(form).withProgram(program).withSubjectType(subjectType).build());
    }
}
