package org.avni.server.web;

import org.avni.server.dao.DownloadableContentRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.DownloadableContent;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.DownloadableContentService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.DownloadableContentRequest;
import org.avni.server.web.response.DownloadableContentResponse;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class DownloadableContentController implements RestControllerResourceProcessor<DownloadableContent> {
    private final DownloadableContentRepository downloadableContentRepository;
    private final DownloadableContentService downloadableContentService;
    private final AccessControlService accessControlService;

    @Autowired
    public DownloadableContentController(DownloadableContentRepository downloadableContentRepository,
                                         DownloadableContentService downloadableContentService,
                                         AccessControlService accessControlService) {
        this.downloadableContentRepository = downloadableContentRepository;
        this.downloadableContentService = downloadableContentService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/downloadableContent")
    @Transactional(readOnly = true)
    @ResponseBody
    public List<DownloadableContentResponse> getAll() {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        return downloadableContentRepository.findAllByIsVoidedFalseOrderByName()
                .stream().map(DownloadableContentResponse::from)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/downloadableContent/{uuid}")
    @Transactional(readOnly = true)
    @ResponseBody
    public ResponseEntity<DownloadableContentResponse> getByUuid(@PathVariable String uuid) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        DownloadableContent content = downloadableContentRepository.findByUuid(uuid);
        if (content == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(DownloadableContentResponse.from(content));
    }

    @PostMapping(value = "/web/downloadableContent")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> save(@RequestBody DownloadableContentRequest request) {
        try {
            accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
            DownloadableContent content = downloadableContentService.createOrUpdate(request);
            return ResponseEntity.ok(DownloadableContentResponse.from(content));
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PutMapping(value = "/web/downloadableContent/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> update(@PathVariable String uuid, @RequestBody DownloadableContentRequest request) {
        try {
            accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
            request.setUuid(uuid);
            DownloadableContent content = downloadableContentService.createOrUpdate(request);
            return ResponseEntity.ok(DownloadableContentResponse.from(content));
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @DeleteMapping(value = "/web/downloadableContent/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> delete(@PathVariable String uuid) {
        try {
            accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
            downloadableContentService.deleteContent(uuid);
            return ResponseEntity.ok().build();
        } catch (BadRequestError e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @GetMapping(value = "/v2/downloadableContent/search/lastModified")
    @Transactional(readOnly = true)
    public CollectionModel<EntityModel<DownloadableContent>> getDownloadableContents(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        return wrap(downloadableContentRepository.findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(
                lastModifiedDateTime.toDate(), CHSEntity.toDate(now), pageable));
    }
}
