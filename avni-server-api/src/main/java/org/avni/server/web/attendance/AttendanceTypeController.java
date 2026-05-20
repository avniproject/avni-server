package org.avni.server.web.attendance;

import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.attendance.AttendanceTypeService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.attendance.AttendanceTypeContract;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class AttendanceTypeController {

    private final AttendanceTypeService attendanceTypeService;
    private final AttendanceTypeRepository attendanceTypeRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final AccessControlService accessControlService;

    public AttendanceTypeController(AttendanceTypeService attendanceTypeService,
                                    AttendanceTypeRepository attendanceTypeRepository,
                                    SubjectTypeRepository subjectTypeRepository,
                                    AccessControlService accessControlService) {
        this.attendanceTypeService = attendanceTypeService;
        this.attendanceTypeRepository = attendanceTypeRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/attendanceType")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<AttendanceTypeContract> getAll(@RequestParam(value = "subjectTypeUUID", required = false) String subjectTypeUUID) {
        List<AttendanceType> attendanceTypes;
        if (subjectTypeUUID != null) {
            SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
            if (subjectType == null) {
                return List.of();
            }
            attendanceTypes = attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(subjectType);
        } else {
            attendanceTypes = attendanceTypeRepository.findAllByIsVoidedFalse();
        }
        return attendanceTypes.stream().map(AttendanceTypeContract::fromEntity).collect(Collectors.toList());
    }

    @GetMapping(value = "/web/attendanceType/{uuid}")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<AttendanceTypeContract> getByUuid(@PathVariable String uuid) {
        AttendanceType attendanceType = attendanceTypeRepository.findByUuid(uuid);
        if (attendanceType == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(AttendanceTypeContract.fromEntity(attendanceType));
    }

    @PostMapping(value = "/web/attendanceType")
    @ResponseBody
    @Transactional
    public ResponseEntity<AttendanceTypeContract> create(@RequestBody AttendanceTypeContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.EditSubjectType);
        contract.setupUuidIfNeeded();
        AttendanceType attendanceType = new AttendanceType();
        attendanceType.assignUUID(contract.getUuid());
        applyContract(contract, attendanceType);
        AttendanceType saved = attendanceTypeService.save(attendanceType);
        return ResponseEntity.ok(AttendanceTypeContract.fromEntity(saved));
    }

    @PutMapping(value = "/web/attendanceType/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<AttendanceTypeContract> update(@PathVariable String uuid, @RequestBody AttendanceTypeContract contract) {
        accessControlService.checkPrivilege(PrivilegeType.EditSubjectType);
        AttendanceType attendanceType = attendanceTypeRepository.findByUuid(uuid);
        if (attendanceType == null) {
            return ResponseEntity.notFound().build();
        }
        applyContract(contract, attendanceType);
        AttendanceType saved = attendanceTypeService.save(attendanceType);
        return ResponseEntity.ok(AttendanceTypeContract.fromEntity(saved));
    }

    @DeleteMapping(value = "/web/attendanceType/{uuid}")
    @ResponseBody
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        accessControlService.checkPrivilege(PrivilegeType.EditSubjectType);
        AttendanceType attendanceType = attendanceTypeRepository.findByUuid(uuid);
        if (attendanceType == null) {
            return ResponseEntity.notFound().build();
        }
        attendanceType.setVoided(true);
        attendanceTypeService.save(attendanceType);
        return ResponseEntity.ok().build();
    }

    private void applyContract(AttendanceTypeContract contract, AttendanceType attendanceType) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(contract.getSubjectTypeUUID());
        if (subjectType == null) {
            throw new BadRequestError("SubjectType not found: %s", contract.getSubjectTypeUUID());
        }
        attendanceType.setSubjectType(subjectType);
        attendanceType.setName(contract.getName());
        attendanceType.setSortOrder(contract.getSortOrder());
        attendanceType.setConfig(contract.getConfig() == null ? new JsonObject() : new JsonObject(contract.getConfig()));
        attendanceType.setVoided(contract.isVoided());
    }
}
