package org.avni.server.importer.batch.csv.creator;

import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProgramEnrolmentCreator {
    private final ProgramEnrolmentRepository programEnrolmentRepository;

    @Autowired
    public ProgramEnrolmentCreator(ProgramEnrolmentRepository programEnrolmentRepository) {
        this.programEnrolmentRepository = programEnrolmentRepository;
    }

    public ProgramEnrolment getProgramEnrolment(String enrolmentId, String identifierForErrorMessage, List<String> allErrorMsgs) throws ValidationException {
        if (enrolmentId == null || enrolmentId.isEmpty()) {
            allErrorMsgs.add(String.format("'%s' is required", identifierForErrorMessage));
            return null;
        }
        ProgramEnrolment programEnrolment = programEnrolmentRepository.findByLegacyIdOrUuid(enrolmentId);
        if (programEnrolment == null) {
            allErrorMsgs.add(String.format("'%s' id '%s' not found in database", identifierForErrorMessage, enrolmentId));
            return null;
        }
        return programEnrolment;
    }
}
