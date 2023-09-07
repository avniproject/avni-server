package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.dao.search.SubjectAssignmentSearchQueryBuilder;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivileges;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.projection.UserWebProjection;
import org.avni.server.service.accessControl.GroupPrivilegeService;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.GroupContract;
import org.avni.server.web.request.UserSubjectAssignmentContract;
import org.avni.server.web.request.webapp.search.SubjectSearchRequest;
import org.hibernate.Session;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class UserSubjectAssignmentService implements NonScopeAwareService {
    private final UserSubjectAssignmentRepository userSubjectAssignmentRepository;
    private final UserRepository userRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final GroupRepository groupRepository;
    private final SubjectSearchRepository subjectSearchRepository;
    private final ConceptRepository conceptRepository;
    private final IndividualRepository individualRepository;
    private final ChecklistService checklistService;
    private final ChecklistItemService checklistItemService;
    private final IndividualRelationshipService individualRelationshipService;
    private final GroupSubjectRepository groupSubjectRepository;
    private final GroupPrivilegeService privilegeService;

    @Autowired
    public UserSubjectAssignmentService(UserSubjectAssignmentRepository userSubjectAssignmentRepository, UserRepository userRepository,
                                        SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository,
                                        GroupRepository groupRepository, SubjectSearchRepository subjectSearchRepository,
                                        ConceptRepository conceptRepository, IndividualRepository individualRepository, ChecklistService checklistService, ChecklistItemService checklistItemService, IndividualRelationshipService individualRelationshipService, GroupSubjectRepository groupSubjectRepository, GroupPrivilegeService privilegeService) {
        this.userSubjectAssignmentRepository = userSubjectAssignmentRepository;
        this.userRepository = userRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.groupRepository = groupRepository;
        this.subjectSearchRepository = subjectSearchRepository;
        this.conceptRepository = conceptRepository;
        this.individualRepository = individualRepository;
        this.checklistService = checklistService;
        this.checklistItemService = checklistItemService;
        this.individualRelationshipService = individualRelationshipService;
        this.groupSubjectRepository = groupSubjectRepository;
        this.privilegeService = privilegeService;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        User user = UserContextHolder.getUserContext().getUser();
        return userSubjectAssignmentRepository.existsByUserAndIsVoidedTrueAndLastModifiedDateTimeGreaterThan(user, CHSEntity.toDate(lastModifiedDateTime));
    }

    public JsonObject getUserSubjectAssignmentMetadata() {
        JsonObject response = new JsonObject();
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        List<SubjectType> subjectTypes = subjectTypeRepository.findAllByIsVoidedFalseAndIsDirectlyAssignableTrue();
        List<ConceptContract> syncAttributes = new ArrayList<>();
        subjectTypes.forEach(st -> {
            if (st.getSyncRegistrationConcept1() != null) {
                addToSyncAttributes(st.getSyncRegistrationConcept1(), syncAttributes);
            }
            if (st.getSyncRegistrationConcept2() != null) {
                addToSyncAttributes(st.getSyncRegistrationConcept2(), syncAttributes);
            }
        });
        List<UserWebProjection> users = userRepository.findAllByOrganisationIdAndIsVoidedFalse(organisation.getId());
        List<GroupContract> groups = groupRepository.findAllByIsVoidedFalse().stream().map(GroupContract::fromEntity).collect(Collectors.toList());
        response.with("users", users)
                .with("subjectTypes", subjectTypes)
                .with("groups", groups)
                .with("syncAttributes", syncAttributes)
                .with("programs", programRepository.findAllByIsVoidedFalse());
        return response;
    }

    private void addToSyncAttributes(String st, List<ConceptContract> syncAttributes) {
        Concept concept = conceptRepository.findByUuid(st);
        syncAttributes.add(ConceptContract.create(concept));
    }

    @Transactional
    public LinkedHashMap<String, Object> searchSubjects(SubjectSearchRequest subjectSearchRequest) {
        List<Map<String, Object>> searchResults = subjectSearchRepository.search(subjectSearchRequest, new SubjectAssignmentSearchQueryBuilder());
        List<Long> subjectIds = searchResults.stream().map(s -> Long.parseLong(s.get("id").toString())).collect(Collectors.toList());
        List<UserSubjectAssignment> userSubjectAssignmentBySubjectIds = userSubjectAssignmentRepository.findUserSubjectAssignmentBySubject_IdIn(subjectIds);

        Map<String, List<User>> groupedSubjects = userSubjectAssignmentBySubjectIds.stream()
                .filter(usa -> !usa.isVoided())
                .collect(Collectors.groupingBy(UserSubjectAssignment::getSubjectIdAsString, TreeMap::new,
                        Collectors.mapping(UserSubjectAssignment::getUser, Collectors.toList())));

        ProjectionFactory pf = new SpelAwareProxyProjectionFactory();
        for (Map<String, Object> searchResult : searchResults) {
            List<UserWebProjection> userWebProjections = Optional
                    .ofNullable(groupedSubjects.get(String.valueOf(searchResult.get("id"))))
                    .map(Collection::stream)
                    .orElseGet(Stream::empty)
                    .map(uw -> pf.createProjection(UserWebProjection.class, uw)).collect(Collectors.toList());
            searchResult.put("assignedUsers", userWebProjections);
        }

        BigInteger totalCount = subjectSearchRepository.getTotalCount(subjectSearchRequest, new SubjectAssignmentSearchQueryBuilder());

        LinkedHashMap<String, Object> recordsMap = new LinkedHashMap<String, Object>();
        recordsMap.put("totalElements", totalCount);
        recordsMap.put("listOfRecords", searchResults);
        return recordsMap;
    }

    public List<UserSubjectAssignment> save(UserSubjectAssignmentContract userSubjectAssignmentRequest) throws ValidationException {
        List<UserSubjectAssignment> userSubjectAssignmentList = new ArrayList<>();
        User user = userRepository.findOne(userSubjectAssignmentRequest.getUserId());
        List<Individual> subjectList = individualRepository.findAllById(userSubjectAssignmentRequest.getSubjectIds());

        for (Individual subject : subjectList) {
            createUpdateAssignment(userSubjectAssignmentRequest.isVoided(), userSubjectAssignmentList, user, subject);
        }
        return this.saveAll(userSubjectAssignmentList);
    }

    public List<UserSubjectAssignment> saveAll(List<UserSubjectAssignment> userSubjectAssignmentList) throws ValidationException {
        List<UserSubjectAssignment> savedUSA = new ArrayList<>();
        for (UserSubjectAssignment userSubjectAssignment : userSubjectAssignmentList) {
            savedUSA.add(save(userSubjectAssignment));
        }
        return savedUSA;
    }

    public UserSubjectAssignment save(UserSubjectAssignment userSubjectAssignment) throws ValidationException {
        if (!userSubjectAssignment.getSubject().getSubjectType().isGroup()) {
            List<GroupSubject> groupSubjects = groupSubjectRepository.findAllByMemberSubject(userSubjectAssignment.getSubject());
            List<Long> directlyAssignableGroups = groupSubjects.stream().filter(groupSubject -> groupSubject.getGroupSubject().getSubjectType().isDirectlyAssignable()).map(groupSubject1 -> groupSubject1.getGroupSubject().getId()).collect(Collectors.toList());
            List<UserSubjectAssignment> userSubjectAssignmentsForGroups = userSubjectAssignmentRepository.findUserSubjectAssignmentByUserIsNotAndSubject_IdIn(userSubjectAssignment.getUser(), directlyAssignableGroups);
            if (userSubjectAssignmentsForGroups.size() > 0) {
                String subjectsAssignedToDifferentUser = userSubjectAssignmentsForGroups.stream().map(x -> x.getSubject().getUuid()).collect(Collectors.joining(","));
                throw new ValidationException(String.format("This subject %s, is member of group(s) - %s who are not assigned to the same user", userSubjectAssignment.getSubject().getFullName(), subjectsAssignedToDifferentUser));
            }
        }
        userSubjectAssignmentRepository.save(userSubjectAssignment);
        updateAuditForUserSubjectAssignment(userSubjectAssignment);
        return userSubjectAssignment;
    }

    private void createUpdateAssignment(boolean assignmentVoided, List<UserSubjectAssignment> userSubjectAssignmentList, User user, Individual subject) {
        UserSubjectAssignment userSubjectAssignment = userSubjectAssignmentRepository.findByUserAndSubject(user, subject);
        if (userSubjectAssignment == null) {
            userSubjectAssignment = UserSubjectAssignment.createNew(user, subject);
        }
        userSubjectAssignment.setVoided(assignmentVoided);
        if (subject.getSubjectType().isGroup()) {
            List<GroupSubject> groupSubjects = groupSubjectRepository.findAllByGroupSubjectAndIsVoidedFalse(subject);
            groupSubjects.forEach(groupSubject -> createUpdateAssignment(assignmentVoided, userSubjectAssignmentList, user, groupSubject.getMemberSubject()));
        }
        userSubjectAssignmentList.add(userSubjectAssignment);
    }

    private void updateAuditForUserSubjectAssignment(UserSubjectAssignment userSubjectAssignment) {
        userSubjectAssignment.updateAudit();
        triggerSyncForSubjectAndItsChildrenForUser(userSubjectAssignment.getSubject(), userSubjectAssignment.getUser());
    }

    private void triggerSyncForSubjectAndItsChildrenForUser(Individual individual, User user) {
        individual.updateAudit();
        individual.getProgramEnrolments()
                .forEach(CHSEntity::updateAudit);
        individual.getProgramEncounters()
                .forEach(CHSEntity::updateAudit);
        individual.getEncounters()
                .forEach(CHSEntity::updateAudit);

        GroupPrivileges groupPrivileges = privilegeService.getGroupPrivileges(user);
        checklistService.findChecklistsByIndividual(individual)
                .stream()
                .filter(groupPrivileges::hasViewPrivilege)
                .forEach(CHSEntity::updateAudit);
        checklistItemService.findChecklistItemsByIndividual(individual)
                .stream()
                .filter(groupPrivileges::hasViewPrivilege)
                .forEach(CHSEntity::updateAudit);
        individualRelationshipService.findByIndividual(individual)
                .stream()
                .filter(individualRelationship ->
                        groupPrivileges.hasViewPrivilege(individualRelationship.getIndividuala()) &&
                                groupPrivileges.hasViewPrivilege(individualRelationship.getIndividualB()))
                .forEach(CHSEntity::updateAudit);
        groupSubjectRepository.findAllByGroupSubjectOrMemberSubject(individual)
                .stream()
                .filter(groupSubject ->
                        groupPrivileges.hasViewPrivilege(groupSubject.getGroupSubject()) &&
                                groupPrivileges.hasViewPrivilege(groupSubject.getMemberSubject())
                )
                .forEach(CHSEntity::updateAudit);
    }
}
