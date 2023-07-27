package org.avni.server.web;

import org.avni.server.dao.UserSubjectAssignmentRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.UserSubjectAssignmentService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.UserSubjectAssignmentContract;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.avni.server.web.response.slice.SlicedResources;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;

@RestController
public class UserSubjectAssignmentController extends AbstractController<UserSubjectAssignment> implements RestControllerResourceProcessor<UserSubjectAssignment> {

    private final UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    private final UserSubjectAssignmentService userSubjectAssignmentService;
    private final AccessControlService accessControlService;

    @Autowired
    public UserSubjectAssignmentController(UserSubjectAssignmentRepository userSubjectAssignmentRepository, UserSubjectAssignmentService userSubjectAssignmentService, AccessControlService accessControlService) {
        this.userSubjectAssignmentRepository = userSubjectAssignmentRepository;
        this.userSubjectAssignmentService = userSubjectAssignmentService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/userSubjectAssignment", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    public PagedResources<Resource<UserSubjectAssignment>> getTasks(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        User user = UserContextHolder.getUserContext().getUser();
        return wrap(userSubjectAssignmentRepository.findByUserAndIsVoidedTrueAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(user, CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }

    @RequestMapping(value = "/userSubjectAssignment/v2", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    public SlicedResources<Resource<UserSubjectAssignment>> getTasksAsSlice(
            @RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
            Pageable pageable) {
        User user = UserContextHolder.getUserContext().getUser();
        return wrap(userSubjectAssignmentRepository.findSliceByUserAndIsVoidedTrueAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(user, CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), pageable));
    }


    @RequestMapping(value = "/web/subjectAssignmentMetadata", method = RequestMethod.GET)
    public JsonObject getSubjectAssignmentMetadataForSearch() {
        accessControlService.checkPrivilege(PrivilegeType.AssignSubject);
        return userSubjectAssignmentService.getUserSubjectAssignmentMetadata();
    }

    @RequestMapping(value = "/web/subjectAssignment/search", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponseEntity<LinkedHashMap<String, Object>> getSubjects(@RequestBody SubjectSearchRequest subjectSearchRequest) {
        return new ResponseEntity<>(userSubjectAssignmentService.searchSubjects(subjectSearchRequest), HttpStatus.OK);
    }

    @RequestMapping(value = "/web/userSubjectAssignment", method = RequestMethod.POST)
    @Transactional
    ResponseEntity<?> save(@RequestBody UserSubjectAssignmentContract userSubjectAssignmentContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditUserConfiguration);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        userSubjectAssignmentService.save(userSubjectAssignmentContract, organisation);
        return ResponseEntity.ok(userSubjectAssignmentContract);
    }

    @Override
    public Resource<UserSubjectAssignment> process(Resource<UserSubjectAssignment> resource) {
        UserSubjectAssignment userSubjectAssignment = resource.getContent();
        resource.removeLinks();
        resource.add(new Link(userSubjectAssignment.getSubject().getUuid(), "subjectUUID"));
        return resource;
    }
}
