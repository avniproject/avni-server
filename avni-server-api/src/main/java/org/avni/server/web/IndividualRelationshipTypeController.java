package org.avni.server.web;

import org.avni.server.dao.individualRelationship.IndividualRelationshipTypeRepository;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.individualRelationship.IndividualRelationshipType;
import org.avni.server.service.IndividualRelationshipTypeService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.IndividualRelationshipTypeContract;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;
import java.util.List;

@RestController
public class IndividualRelationshipTypeController {
    private final IndividualRelationshipTypeRepository individualRelationshipTypeRepository;
    private final IndividualRelationshipTypeService individualRelationshipTypeService;
    private final AccessControlService accessControlService;

    public IndividualRelationshipTypeController(IndividualRelationshipTypeRepository individualRelationshipTypeRepository,
                                                IndividualRelationshipTypeService individualRelationshipTypeService, AccessControlService accessControlService) {
        this.individualRelationshipTypeRepository = individualRelationshipTypeRepository;
        this.individualRelationshipTypeService = individualRelationshipTypeService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/relationshipType")
    @ResponseBody
    public List<IndividualRelationshipTypeContract> getAllIndividualRelationshipTypes() {
        return individualRelationshipTypeService.getAllRelationshipTypes(false);
    }

    @PostMapping(value = "/web/relationshipType")
    @ResponseBody
    @Transactional
    public ResponseEntity<IndividualRelationshipTypeContract> newRelationshipType(@RequestBody IndividualRelationshipTypeContract relationshipTypeContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditRelation);
        IndividualRelationshipType individualRelationshipType = individualRelationshipTypeService.saveRelationshipType(relationshipTypeContract);
        return ResponseEntity.ok(IndividualRelationshipTypeContract.fromEntity(individualRelationshipType));
    }

    @DeleteMapping(value = "/web/relationshipType/{id}")
    @ResponseBody
    @Transactional
    public void deleteIndividualRelationshipType(@PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditRelation);
        IndividualRelationshipType individualRelationshipType = individualRelationshipTypeRepository.findOne(id);
        if (individualRelationshipType != null) {
            individualRelationshipType.setVoided(true);
            individualRelationshipTypeRepository.save(individualRelationshipType);
        }
    }
}
