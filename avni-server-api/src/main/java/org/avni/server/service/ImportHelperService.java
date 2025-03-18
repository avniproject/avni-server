package org.avni.server.service;

import org.avni.server.application.FormElement;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class ImportHelperService {
    private static final Logger logger = LoggerFactory.getLogger(ImportHelperService.class);

    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;

    @Autowired
    public ImportHelperService(
            SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
    }

    public SubjectType getSubjectType(String subjectTypeName) {
        SubjectType subjectType = subjectTypeRepository.findByName(subjectTypeName);
        return subjectType;
    }

    public String getHeaderName(FormElement formElement) {
        String conceptName = formElement.getConcept().getName();
        if (formElement.getGroup() != null) {
            FormElement parentFormElement = formElement.getGroup();
            String parentChildName = parentFormElement.getConcept().getName() + "|" + conceptName;
            return parentFormElement.isRepeatable() ? String.format("\"%s|1\"", parentChildName) : String.format("\"%s\"", parentChildName);
        }
        return "\"" + conceptName + "\"";
    }

    Program getProgram(String programName) {
        Program program = programRepository.findByName(programName);
        return program;
    }
}