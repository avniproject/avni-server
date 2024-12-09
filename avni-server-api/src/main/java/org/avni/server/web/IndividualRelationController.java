package org.avni.server.web;

import org.avni.server.dao.individualRelationship.IndividualRelationRepository;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.individualRelationship.IndividualRelation;
import org.avni.server.service.IndividualRelationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.IndividualRelationContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@RestController
public class IndividualRelationController {
    private final IndividualRelationRepository individualRelationRepository;
    private final IndividualRelationService individualRelationService;
    private final AccessControlService accessControlService;

    @Autowired
    public IndividualRelationController(IndividualRelationRepository individualRelationRepository,
                                        IndividualRelationService individualRelationService, AccessControlService accessControlService) {
        this.individualRelationRepository = individualRelationRepository;
        this.individualRelationService = individualRelationService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/relation")
    @ResponseBody
    public List<IndividualRelationContract> getAllIndividualRelations() {
        return individualRelationService.getAll();
    }

    @GetMapping(value = "/web/relation/{id}")
    @ResponseBody
    public ResponseEntity<IndividualRelationContract> getIndividualRelation(@PathVariable Long id) {
        Optional<IndividualRelation> relation = individualRelationRepository.findById(id);
        return relation.map(individualRelation ->
                ResponseEntity.ok(individualRelationService.toResponseObject(individualRelation)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/web/relation")
    @ResponseBody
    @Transactional
    public ResponseEntity<IndividualRelationContract> newIndividualRelation(@RequestBody IndividualRelationContract individualRelationContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditRelation);
        IndividualRelation relation = individualRelationService.saveRelation(individualRelationContract);
        return ResponseEntity.ok(individualRelationService.toResponseObject(relation));
    }

    @PostMapping(value = "/web/relation/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity saveIndividualRelation(@PathVariable Long id, @RequestBody IndividualRelationContract individualRelationContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditRelation);
        Optional<IndividualRelation> relation = individualRelationRepository.findById(id);
        if (!relation.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        IndividualRelation individualRelation = relation.get();
        individualRelation.setName(individualRelationContract.getName());
        individualRelationService.saveGenderMappings(individualRelationContract, individualRelation);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping(value = "/web/relation/{id}")
    @ResponseBody
    @Transactional
    public void deleteIndividualRelation(@PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditRelation);
        individualRelationService.deleteRelation(id);
    }
}
