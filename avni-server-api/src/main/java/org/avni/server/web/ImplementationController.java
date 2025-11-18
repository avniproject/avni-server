package org.avni.server.web;

import org.avni.server.domain.Concept;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
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
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;

@RestController
public class ImplementationController implements RestControllerResourceProcessor<Concept> {
    private final ImplementationService implementationService;
    private final AccessControlService accessControlService;
    private final BundleService bundleService;

    @Autowired
    public ImplementationController(ImplementationService implementationService,
                                   AccessControlService accessControlService,
                                   BundleService bundleService) {
        this.implementationService = implementationService;
        this.accessControlService = accessControlService;
        this.bundleService = bundleService;
    }

    @RequestMapping(value = "/implementation/export/{includeLocations}", method = RequestMethod.GET)
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

    private HttpHeaders getHttpHeaders() {
        HttpHeaders header = new HttpHeaders();
        header.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=impl.zip");
        header.add("Cache-Control", "no-cache, no-store, must-revalidate");
        header.add("Pragma", "no-cache");
        header.add("Expires", "0");
        return header;
    }
}
