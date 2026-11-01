package org.avni.server.web;

import org.avni.server.domain.Concept;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.orgdata.DeleteOrgDataJobService;
import org.avni.server.service.BundleService;
import org.avni.server.service.ImplementationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.validation.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;

@RestController
public class ImplementationController implements RestControllerResourceProcessor<Concept> {
    private final ImplementationService implementationService;
    private final AccessControlService accessControlService;
    private final BundleService bundleService;
    private final DeleteOrgDataJobService deleteOrgDataJobService;

    @Autowired
    public ImplementationController(ImplementationService implementationService,
                                   AccessControlService accessControlService,
                                   BundleService bundleService,
                                   DeleteOrgDataJobService deleteOrgDataJobService) {
        this.implementationService = implementationService;
        this.accessControlService = accessControlService;
        this.bundleService = bundleService;
        this.deleteOrgDataJobService = deleteOrgDataJobService;
    }

    @RequestMapping(value = "/implementation/export/{includeLocations}", method = RequestMethod.GET)
    @Transactional(readOnly = true, rollbackFor = Exception.class)
    public ResponseEntity<ByteArrayResource> export(@PathVariable boolean includeLocations) throws Exception {
        accessControlService.checkPrivilege(PrivilegeType.DownloadBundle);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        ByteArrayOutputStream baos = bundleService.createBundle(organisation, includeLocations);
        byte[] baosByteArray = baos.toByteArray();

        return ResponseEntity.ok()
                .headers(getHttpHeaders())
                .contentLength(baosByteArray.length)
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(new ByteArrayResource(baosByteArray));
    }

    @RequestMapping(value = "/implementation/delete", method = RequestMethod.DELETE)
    public ResponseEntity<String> delete(@RequestParam("deleteMetadata") boolean deleteMetadata,
                                       @RequestParam("deleteAdminConfig") boolean deleteAdminConfig) {
        try {
            implementationService.deleteImplementationData(deleteMetadata, deleteAdminConfig);
        } catch (ValidationException validationException) {
            return new ResponseEntity<>(validationException.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(HttpStatus.OK);

    }

    /**
     * Same checks and responses as /implementation/delete, but the deletion runs as a background job (avni-webapp#1639):
     * answers 202 with the job uuid at once. Poll /implementation/delete/job/status for the outcome.
     */
    @RequestMapping(value = "/implementation/delete/job", method = RequestMethod.POST)
    public ResponseEntity<String> startDeleteJob(@RequestParam("deleteMetadata") boolean deleteMetadata,
                                                 @RequestParam("deleteAdminConfig") boolean deleteAdminConfig) {
        try {
            implementationService.assertCanDeleteImplementationData(deleteMetadata, deleteAdminConfig);
        } catch (ValidationException validationException) {
            return new ResponseEntity<>(validationException.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(deleteOrgDataJobService.start(deleteMetadata, deleteAdminConfig), HttpStatus.ACCEPTED);
    }

    @RequestMapping(value = "/implementation/delete/job/status", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public BatchJobStatus deleteJobStatus() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return deleteOrgDataJobService.getLatestJobStatus(UserContextHolder.getOrganisation());
    }

    private HttpHeaders getHttpHeaders() {
        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=impl.zip");
        header.add("Cache-Control", "no-cache, no-store, must-revalidate");
        header.add("Pragma", "no-cache");
        header.add("Expires", "0");
        return header;
    }
}
