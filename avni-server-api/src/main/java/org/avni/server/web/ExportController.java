package org.avni.server.web;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.IOUtils;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.JobStatus;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.CHSBaseEntity;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Program;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.exporter.ExportJobService;
import org.avni.server.service.ExportS3Service;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.external.request.export.ExportJobRequest;
import org.avni.server.web.external.request.export.ExportOutput;
import org.avni.server.web.external.request.export.ExportV2JobRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.avni.server.exporter.v2.ExportV2ValidationHelper.*;

@RestController
public class ExportController {
    private final ExportJobService exportJobService;
    private final ExportS3Service exportS3Service;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final AccessControlService accessControlService;

    @Autowired
    public ExportController(ExportJobService exportJobService, ExportS3Service exportS3Service, SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository, AccessControlService accessControlService) {
        this.exportJobService = exportJobService;
        this.exportS3Service = exportS3Service;
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/export", method = RequestMethod.POST)
    public ResponseEntity<?> getVisitData(@RequestBody ExportJobRequest exportJobRequest) {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        return exportJobService.runExportJob(exportJobRequest);
    }

    @RequestMapping(value = "/export/v2", method = RequestMethod.POST)
    public ResponseEntity<?> getVisitDataV2(@RequestBody ExportV2JobRequest exportJobRequest) {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        ExportOutput exportOutput = getExportOutput(exportJobRequest);
        ResponseEntity<?> validationErrorResponseEntity = validateHeader(exportOutput);
        if(validationErrorResponseEntity != null) {
            return validationErrorResponseEntity;
        }
        ResponseEntity<?> uuidValidationErrorResponseEntity = validateEntityUUIDs(exportOutput);
        if(uuidValidationErrorResponseEntity != null) {
            return uuidValidationErrorResponseEntity;
        }
        return exportJobService.runExportV2Job(exportJobRequest);
    }

    private ExportOutput getExportOutput(ExportV2JobRequest exportJobRequest) {
        return ObjectMapperSingleton.getObjectMapper().convertValue(exportJobRequest.getIndividual(), new TypeReference<ExportOutput>() {});
    }

    private ResponseEntity<?> validateHeader(ExportOutput exportOutput) {
        return exportOutput.validate();
    }

    @RequestMapping(value = "/export/status", method = RequestMethod.GET)
    public Page<JobStatus> getUploadStatus(Pageable pageable) {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        return exportJobService.getAll(pageable);
    }

    @RequestMapping(value = "/export/download", method = RequestMethod.GET)
    public ResponseEntity<?> downloadFile(@RequestParam String fileName) throws IOException {
        InputStream inputStream = exportS3Service.downloadFile(fileName);
        byte[] bytes = IOUtils.toByteArray(inputStream);
        return ResponseEntity.ok()
                .headers(getHttpHeaders(fileName))
                .contentLength(bytes.length)
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(new ByteArrayResource(bytes));
    }

    private HttpHeaders getHttpHeaders(String filename) {
        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=".concat(filename));
        header.add("Cache-Control", "no-cache, no-store, must-revalidate");
        header.add("Pragma", "no-cache");
        header.add("Expires", "0");
        return header;
    }

    private ResponseEntity<List<String>> validateEntityUUIDs(ExportOutput exportOutput) {
        List<String> errorList = new ArrayList<>();
        String subjectTypeUUID = exportOutput.getUuid();
        SubjectType individualType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        validateEntity(individualType, INDIVIDUAL, subjectTypeUUID, errorList);

        exportOutput.getEncounters().forEach(e -> {
            String programEncounterTypeUUID = e.getUuid();
            EncounterType encounterType = encounterTypeRepository.findByUuid(programEncounterTypeUUID);
            validateEntity(encounterType, ENCOUNTER, programEncounterTypeUUID, errorList);
        });

        exportOutput.getGroups().forEach(g -> {
            String groupSubjectTypeUUID = g.getUuid();
            SubjectType groupSubjectType = subjectTypeRepository.findByUuid(groupSubjectTypeUUID);
            validateEntity(groupSubjectType, GROUP_SUBJECT, groupSubjectTypeUUID, errorList);

            g.getEncounters().forEach(ge -> {
                String programEncounterTypeUUID = ge.getUuid();
                EncounterType encounterType = encounterTypeRepository.findByUuid(programEncounterTypeUUID);
                validateEntity(encounterType, GROUP_SUBJECT_ENCOUNTER, programEncounterTypeUUID, errorList);
            });
        });

        exportOutput.getPrograms().forEach(p -> {
            String programUUID = p.getUuid();
            Program program = programRepository.findByUuid(programUUID);
            validateEntity(program, PROGRAM_ENROLMENT, programUUID, errorList);

            p.getEncounters().forEach(pe -> {
                String programEncounterTypeUUID = pe.getUuid();
                EncounterType encounterType = encounterTypeRepository.findByUuid(programEncounterTypeUUID);
                validateEntity(encounterType, PROGRAM_ENCOUNTER, programEncounterTypeUUID, errorList);
            });
        });

        if(!errorList.isEmpty()) {
            return ResponseEntity.badRequest().body(errorList);
        }
        return null;
    }

    private void validateEntity(CHSBaseEntity entity, String entityType, String entityUUID, List<String> errorList) {
        if(entity == null) {
            errorList.add(String.format("Invalid UUID %s specified for %s type field", entityUUID, entityType));
        }
    }

}
