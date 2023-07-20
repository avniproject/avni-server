package org.avni.server.web;

import org.avni.server.application.projections.DocumentationProjection;
import org.avni.server.dao.DocumentationRepository;
import org.avni.server.domain.Documentation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.DocumentationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.webapp.documentation.DocumentationContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.List;

@RestController
public class DocumentationController extends AbstractController<Documentation> implements RestControllerResourceProcessor<Documentation> {

    private final DocumentationRepository documentationRepository;
    private final DocumentationService documentationService;
    private final AccessControlService accessControlService;


    @Autowired
    public DocumentationController(DocumentationRepository documentationRepository, DocumentationService documentationService, AccessControlService accessControlService) {
        this.documentationRepository = documentationRepository;
        this.documentationService = documentationService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/documentation")
    @ResponseBody
    public List<DocumentationContract> getAllDocumentNodes() {
        return documentationService.getAllNonVoided();
    }

    @PostMapping(value = "/web/documentation")
    @Transactional
    @ResponseBody
    public ResponseEntity<DocumentationContract> saveDocumentation(@RequestBody DocumentationContract documentationContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditDocumentation);
        Documentation savedDocumentation = documentationService.saveDocumentation(documentationContract);
        return ResponseEntity.ok(DocumentationContract.fromDocumentation(savedDocumentation));
    }

    @RequestMapping(value = "/search/documentation", method = RequestMethod.GET)
    public Page<DocumentationProjection> searchDocumentation(@RequestParam String name, Pageable pageable) {
        return documentationRepository.findByIsVoidedFalseAndNameIgnoreCaseContains(name, pageable);
    }

    @DeleteMapping(value = "/web/documentation/{id}")
    @ResponseBody
    @Transactional
    public void deleteDocumentation(@PathVariable String id) {
        accessControlService.checkPrivilege(PrivilegeType.EditDocumentation);
        Documentation documentation = documentationRepository.findByUuid(id);
        if (documentation != null) {
            documentation.setVoided(true);
            documentation.setName(ReactAdminUtil.getVoidedName(documentation.getName(), documentation.getId()));
            documentationRepository.save(documentation);
        }
    }
}
