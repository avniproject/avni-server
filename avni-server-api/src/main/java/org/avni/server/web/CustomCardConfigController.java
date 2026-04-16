package org.avni.server.web;

import org.avni.server.dao.CustomCardConfigRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.CustomCardConfig;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.CustomCardConfigService;
import org.avni.server.service.S3Service;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.CustomCardConfigRequest;
import org.avni.server.web.response.CustomCardConfigResponse;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.String.format;

@RestController
public class CustomCardConfigController implements RestControllerResourceProcessor<CustomCardConfig> {
    private final CustomCardConfigRepository customCardConfigRepository;
    private final CustomCardConfigService customCardConfigService;
    private final AccessControlService accessControlService;
    private final S3Service s3Service;

    @Autowired
    public CustomCardConfigController(CustomCardConfigRepository customCardConfigRepository,
                                      CustomCardConfigService customCardConfigService,
                                      AccessControlService accessControlService,
                                      S3Service s3Service) {
        this.customCardConfigRepository = customCardConfigRepository;
        this.customCardConfigService = customCardConfigService;
        this.accessControlService = accessControlService;
        this.s3Service = s3Service;
    }

    @GetMapping(value = "/web/customCardConfig")
    @Transactional(readOnly = true)
    @ResponseBody
    public List<CustomCardConfigResponse> getAll() {
        return customCardConfigRepository.findAllByIsVoidedFalseOrderByName()
                .stream().map(CustomCardConfigResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/customCardConfig/{uuid}")
    @Transactional(readOnly = true)
    @ResponseBody
    public ResponseEntity<CustomCardConfigResponse> getByUuid(@PathVariable String uuid) {
        CustomCardConfig config = customCardConfigRepository.findByUuid(uuid);
        if (config == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(CustomCardConfigResponse.from(config));
    }

    @PostMapping(value = "/web/customCardConfig")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> save(@RequestBody CustomCardConfigRequest request) {
        try {
            accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
            CustomCardConfig config = customCardConfigService.createOrUpdateCustomCardConfig(request);
            return ResponseEntity.ok(CustomCardConfigResponse.from(config));
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping(value = "/web/customCardConfig/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> update(@PathVariable String uuid, @RequestBody CustomCardConfigRequest request) {
        try {
            accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
            request.setUuid(uuid);
            CustomCardConfig config = customCardConfigService.createOrUpdateCustomCardConfig(request);
            return ResponseEntity.ok(CustomCardConfigResponse.from(config));
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping(value = "/web/customCardConfig/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> delete(@PathVariable String uuid) {
        try {
            accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
            customCardConfigService.deleteConfig(uuid);
            return ResponseEntity.ok().build();
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping(value = "/web/customCardConfig/{uuid}/upload")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> uploadHtml(@PathVariable String uuid, @RequestPart("file") MultipartFile file) {
        try {
            accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
            String s3Key = customCardConfigService.uploadHtmlFile(uuid, file);
            return ResponseEntity.ok(s3Key);
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @RequestMapping(value = "/customCardConfigFile/{fileName:.+}", method = RequestMethod.GET)
    @Transactional(readOnly = true)
    public ResponseEntity<?> serveCustomCardConfigFile(@PathVariable String fileName) {
        Organisation organisation = UserContextHolder.getOrganisation();
        if (organisation == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        try {
            String s3Path = format("%s/%s", CustomCardConfigService.CUSTOM_CARD_CONFIGS_SUBDIR, fileName);
            InputStream contentStream = s3Service.getOrgScopedContent(s3Path, organisation);
            return ResponseEntity.ok().body(new InputStreamResource(contentStream));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(format("Error serving %s", fileName));
        }
    }

    @GetMapping(value = "/v2/customCardConfig/search/lastModified")
    @Transactional(readOnly = true)
    public CollectionModel<EntityModel<CustomCardConfig>> getCustomCardConfigs(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(customCardConfigRepository.findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
                lastModifiedDateTime.toDate(), CHSEntity.toDate(now), pageable));
    }
}
