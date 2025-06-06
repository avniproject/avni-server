package org.avni.server.web;

import jakarta.servlet.http.HttpServletResponse;
import org.avni.server.application.FormElement;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.JobStatus;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.JobService;
import org.avni.server.importer.batch.csv.writer.LocationWriter;
import org.avni.server.importer.batch.csv.writer.header.EncounterUploadMode;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.response.ImportSampleCheckResponse;
import org.avni.server.web.util.ErrorBodyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;
import static org.avni.server.util.AvniFiles.*;
import static org.springframework.http.MediaType.*;

@RestController
public class ImportController {
    private final Logger logger;
    private final JobService jobService;
    private final BulkUploadS3Service bulkUploadS3Service;
    private final ImportService importService;
    private final S3Service s3Service;
    private final IndividualService individualService;
    private final LocationService locationService;
    private final FormElementRepository formElementRepository;
    private final AccessControlService accessControlService;
    private final ErrorBodyBuilder errorBodyBuilder;
    private final LocationHierarchyService locationHierarchyService;

    @Autowired
    public ImportController(JobService jobService,
                            BulkUploadS3Service bulkUploadS3Service,
                            ImportService importService,
                            S3Service s3Service,
                            IndividualService individualService,
                            LocationService locationService,
                            FormElementRepository formElementRepository, AccessControlService accessControlService, ErrorBodyBuilder errorBodyBuilder, LocationHierarchyService locationHierarchyService) {
        this.jobService = jobService;
        this.bulkUploadS3Service = bulkUploadS3Service;
        this.importService = importService;
        this.s3Service = s3Service;
        this.individualService = individualService;
        this.locationService = locationService;
        this.formElementRepository = formElementRepository;
        this.accessControlService = accessControlService;
        this.errorBodyBuilder = errorBodyBuilder;
        this.locationHierarchyService = locationHierarchyService;
        logger = LoggerFactory.getLogger(getClass());
    }

    @RequestMapping(value = "/web/importSample", method = RequestMethod.GET)
    public void getSampleImportFile(@RequestParam String uploadType,
                                    @RequestParam(value = "locationHierarchy", required = false) String locationHierarchy,
                                    @RequestParam(value = "locationUploadMode", required = false) LocationWriter.LocationUploadMode locationUploadMode,
                                    @RequestParam(value = "encounterUploadMode", required = false) String encounterUploadMode,
                                    HttpServletResponse response) throws IOException, InvalidConfigurationException {
        response.setContentType("text/csv");
        importService.getSampleImportFile(uploadType, locationHierarchy, locationUploadMode, EncounterUploadMode.fromString(encounterUploadMode), response);
    }

    @RequestMapping(value = "/web/importSampleDownloadable", method = RequestMethod.GET)
    public ResponseEntity<ImportSampleCheckResponse> checkSampleImportFileIsDownloadable(@RequestParam String uploadType,
                                                                                         @RequestParam(value = "locationHierarchy", required = false) String locationHierarchy,
                                                                                         @RequestParam(value = "locationUploadMode", required = false) LocationWriter.LocationUploadMode locationUploadMode,
                                                                                         @RequestParam(value = "encounterUploadMode", required = false) String encounterUploadMode,
                                                                                         HttpServletResponse response) {

        try {
            importService.getSampleImportFile(uploadType, locationHierarchy, locationUploadMode,
                    EncounterUploadMode.fromString(encounterUploadMode), null);
            return ResponseEntity.ok(new ImportSampleCheckResponse(true, "Sample import file is downloadable"));
        } catch (Exception e) {
            return ResponseEntity.ok(new ImportSampleCheckResponse(false, e.getMessage()));
        }
    }

    @RequestMapping(value = "/web/importTypes", method = RequestMethod.GET)
    public ResponseEntity getImportTypes() {
        return ResponseEntity.ok(importService.getImportTypes());
    }

    @PostMapping("/import/new")
    public ResponseEntity<?> importFile(@RequestParam MultipartFile file,
                                        @RequestParam String type,
                                        @RequestParam boolean autoApprove,
                                        @RequestParam String locationUploadMode,
                                        @RequestParam String locationHierarchy,
                                        @RequestParam String encounterUploadMode) throws IOException {

        accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
        try {
            assertTrue(!StringUtils.isEmpty(type), "File type not provided");
            String mimeType = detectMimeType(file);
            if ("application/vnd.ms-excel".equals(mimeType)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Try re-uploading using Chrome browser.");
            }
            validateFile(file, type.equals("metadataZip") ? ZipFiles : Collections.singletonList("text/csv"));
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

        String uuid = UUID.randomUUID().toString();
        User user = UserContextHolder.getUserContext().getUser();
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        try {
            ObjectInfo storedFileInfo = type.equals("metadataZip") ? bulkUploadS3Service.uploadZip(file, uuid) : bulkUploadS3Service.uploadFile(file, uuid);
            jobService.create(uuid, type, file.getOriginalFilename(), storedFileInfo, user.getId(), organisation.getUuid(), autoApprove, locationUploadMode, locationHierarchy, encounterUploadMode);
        } catch (JobParametersInvalidException | JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException | JobRestartException e) {
            logger.error(format("Bulk upload initiation failed. file:'%s', user:'%s'", file.getOriginalFilename(), user.getUsername()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(e));
        } catch (IOException e) {
            logger.error(format("Bulk upload initiation failed. file:'%s', user:'%s'", file.getOriginalFilename(), user.getUsername()), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(format("Unable to process file. %s", e.getMessage())));
        } catch (Exception e) {
            logger.error(format("Bulk upload initiation failed. file:'%s', user:'%s'", file.getOriginalFilename(), user.getUsername()), e);
            if (!type.equals("metadataZip")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(format("%s does not appear to be a valid .csv file.", file.getOriginalFilename()));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBodyBuilder.getErrorBody(format("Unable to process file. %s", e.getMessage())));
        }
        return ResponseEntity.ok(true);
    }

    @GetMapping("/import/status")
    public Page<JobStatus> getUploadStats(Pageable pageable) {
        return jobService.getAll(pageable);
    }

    @GetMapping(value = "/import/errorfile",
            produces = TEXT_PLAIN_VALUE,
            consumes = APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> getDocument(@RequestParam String jobUuid) {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        InputStream file = bulkUploadS3Service.downloadErrorFile(jobUuid);
        return ResponseEntity.ok()
                .contentType(TEXT_PLAIN)
                .cacheControl(CacheControl.noCache())
                .header("Content-Disposition", "attachment; ")
                .body(new InputStreamResource(file));
    }

    @GetMapping(value = "/import/inputFile",
            produces = TEXT_PLAIN_VALUE,
            consumes = APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<InputStreamResource> getInputDocument(@RequestParam String filePath) {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        InputStream file = bulkUploadS3Service.downloadInputFile(filePath);
        return ResponseEntity.ok()
                .contentType(TEXT_PLAIN)
                .cacheControl(CacheControl.noCache())
                .header("Content-Disposition", "attachment; ")
                .body(new InputStreamResource(file));
    }

    @GetMapping("/upload/media")
    public JsonObject uploadMedia(@RequestParam("url") String url,
                                  @RequestParam("oldValue") String oldValue) {
        accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
        JsonObject response = new JsonObject();
        String decodedURL = new String(Base64.getDecoder().decode(url));
        try {
            String obsValue = s3Service.getObservationValueForUpload(decodedURL, oldValue);
            response.with("value", obsValue);
        } catch (Exception e) {
            response.with("error", e.getMessage());
        }
        return response;
    }

    @GetMapping("/upload")
    public JsonObject getSubjectOrLocationObsValue(@RequestParam("type") String type,
                                                   @RequestParam("ids") String ids,
                                                   @RequestParam("formElementUuid") String formElementUuid) {
        accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
        FormElement formElement = formElementRepository.findByUuid(formElementUuid);
        JsonObject response = new JsonObject();
        if (ConceptDataType.Location.toString().equals(type)) {
            response.with("value", locationService.getObservationValueForUpload(formElement, ids));
        } else if (ConceptDataType.Subject.toString().equals(type)) {
            response.with("value", individualService.getObservationValueForUpload(formElement, ids));
        }
        return response;
    }

    @GetMapping(value = "/web/locationHierarchies")
    @ResponseBody
    public Map<String, String> getAllAddressLevelTypeHierarchies() {
        try {
            return locationHierarchyService.determineAddressHierarchiesForAllAddressLevelTypesInOrg();
        } catch (Exception exception) {
            logger.error("Error getting web locationHierarchies", exception);
            return null;
        }
    }
}
