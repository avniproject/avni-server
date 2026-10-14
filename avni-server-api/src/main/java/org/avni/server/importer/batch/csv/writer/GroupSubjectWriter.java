package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.SubjectTypeSettingKey;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.GroupSubjectRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationRepository;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.domain.GroupRole;
import org.avni.server.domain.GroupSubject;
import org.avni.server.domain.Individual;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.individualRelationship.IndividualRelation;
import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.avni.server.importer.batch.csv.creator.DateCreator;
import org.avni.server.importer.batch.csv.writer.header.GroupMemberHeaders;
import org.avni.server.importer.batch.csv.writer.header.HouseholdMemberHeaders;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.GroupSubjectService;
import org.avni.server.service.HouseholdService;
import org.avni.server.util.DateTimeUtil;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class GroupSubjectWriter implements ItemWriter<Row>, Serializable {
    private final GroupSubjectRepository groupSubjectRepository;
    private final GroupRoleRepository groupRoleRepository;
    private final IndividualRepository individualRepository;
    private final IndividualRelationRepository individualRelationRepository;
    private final IndividualRelationshipRepository individualRelationshipRepository;
    private final HouseholdService householdService;
    private final GroupSubjectService groupSubjectService;
    private final ConceptRepository conceptRepository;

    @Autowired
    public GroupSubjectWriter(GroupSubjectRepository groupSubjectRepository,
                              GroupRoleRepository groupRoleRepository,
                              IndividualRepository individualRepository,
                              IndividualRelationRepository individualRelationRepository,
                              IndividualRelationshipRepository individualRelationshipRepository,
                              HouseholdService householdService, GroupSubjectService groupSubjectService,
                              ConceptRepository conceptRepository) {
        this.groupSubjectRepository = groupSubjectRepository;
        this.groupRoleRepository = groupRoleRepository;
        this.individualRepository = individualRepository;
        this.individualRelationRepository = individualRelationRepository;
        this.individualRelationshipRepository = individualRelationshipRepository;
        this.householdService = householdService;
        this.groupSubjectService = groupSubjectService;
        this.conceptRepository = conceptRepository;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws Exception {
        for (Row row : chunk.getItems()) write(row);
    }

    private void write(Row row) throws Exception {
        List<String> allErrorMsgs = new ArrayList<>();

        GroupSubject groupSubject = getNewGroupSubject(row, allErrorMsgs);
        if (allErrorMsgs.size() > 0 || groupSubject == null) {
            throw new Exception(String.join(", ", allErrorMsgs));
        }
        boolean isHousehold = groupSubject.getGroupSubject().getSubjectType().isHousehold();
        GroupRole groupRole = isHousehold
                ? getHouseholdGroupRole(row.getBool(HouseholdMemberHeaders.isHeadOfHousehold),
                        allErrorMsgs,
                        HouseholdMemberHeaders.isHeadOfHousehold,
                        groupSubject.getGroupSubject().getSubjectType().getId())
                : getGroupRole(row.get(GroupMemberHeaders.role),
                        allErrorMsgs,
                        GroupMemberHeaders.role,
                        groupSubject.getGroupSubject().getSubjectType().getId());
        DateCreator dateCreator = new DateCreator();
        LocalDate membershipStartDate = dateCreator.getDate(row, GroupMemberHeaders.membershipStartDate, allErrorMsgs, null);
        LocalDate membershipEndDate = dateCreator.getDate(row, GroupMemberHeaders.membershipEndDate, allErrorMsgs, null);
        validateMembershipDates(membershipStartDate, membershipEndDate, allErrorMsgs);
        String removalReason = row.get(GroupMemberHeaders.removalReason);
        boolean hasRemovalReason = removalReason != null && !removalReason.isEmpty();
        if (hasRemovalReason && membershipEndDate == null) {
            allErrorMsgs.add(String.format("%s is given only with %s", GroupMemberHeaders.removalReason, GroupMemberHeaders.membershipEndDate));
        }
        if (allErrorMsgs.size() > 0) {
            throw new Exception(String.join(", ", allErrorMsgs));
        }

        List<GroupSubject> existingGroupSubjects = groupSubjectRepository.findAllByGroupSubjectAndMemberSubject(groupSubject.getGroupSubject(), groupSubject.getMemberSubject());
        GroupSubject matchingGroupSubject = findMatchingGroupSubject(existingGroupSubjects, membershipStartDate, membershipEndDate, allErrorMsgs);
        if (allErrorMsgs.size() > 0) {
            throw new Exception(String.join(", ", allErrorMsgs));
        }
        if (matchingGroupSubject != null) {
            if (!matchingGroupSubject.getGroupRole().getId().equals(groupRole.getId())) {
                throw new Exception("Role differs from the existing membership. Change it in the app.");
            }
            if (membershipEndDate != null && !matchingGroupSubject.isVoided()) {
                String removalReasonConceptUUID = getRemovalReasonConceptUUID(groupSubject.getGroupSubject().getSubjectType(), removalReason, hasRemovalReason, allErrorMsgs);
                if (allErrorMsgs.size() > 0) {
                    throw new Exception(String.join(", ", allErrorMsgs));
                }
                endMembership(matchingGroupSubject, membershipEndDate, removalReasonConceptUUID);
                groupSubjectService.save(matchingGroupSubject);
            }
            return;
        }

        groupSubject.setGroupRole(groupRole);
        String removalReasonConceptUUID = membershipEndDate == null ? null : getRemovalReasonConceptUUID(groupSubject.getGroupSubject().getSubjectType(), removalReason, hasRemovalReason, allErrorMsgs);
        IndividualRelationship individualRelationship = null;
        if (isHousehold && membershipEndDate == null) {
            if (groupRole.getRole().equals("Member")) {
                IndividualRelation individualRelation = getRelationWithHeadOfHousehold(row.get(HouseholdMemberHeaders.relationshipWithHeadOfHousehold), allErrorMsgs);
                if (individualRelation != null) {
                    individualRelationship = householdService.determineRelationshipWithHeadOfHousehold(groupSubject, individualRelation, allErrorMsgs);
                }
            } else if (groupRole.getRole().equals("Head of household")) {
                Individual existingHeadOfHousehold = householdService.getHeadOfHouseholdForGroupSubject(groupSubject);
                if (existingHeadOfHousehold != null && !existingHeadOfHousehold.getId().equals(groupSubject.getMemberSubject().getId())) {
                    allErrorMsgs.add(String.format("Member with id '%s' cannot be head of household because another member is already head of household.", groupSubject.getMemberSubject().getLegacyId()));
                }
            }
        }

        if (allErrorMsgs.size() > 0) {
            throw new Exception(String.join(", ", allErrorMsgs));
        }

        groupSubject.setMembershipStartDate(membershipStartDate != null ? membershipStartDate.toDateTimeAtStartOfDay() : LocalDate.now().toDateTimeAtCurrentTime());
        if (membershipEndDate != null) {
            endMembership(groupSubject, membershipEndDate, removalReasonConceptUUID);
        }
        groupSubject.assignUUIDIfRequired();
        groupSubjectService.save(groupSubject);
        saveRelationshipWithHeadOfHousehold(individualRelationship);
    }

    private GroupSubject getNewGroupSubject(Row row, List<String> errorMsgs) {
        if (row.getHeaders().length == 0) {
            errorMsgs.add("No headers found in file");
            return null;
        }
        String groupIdIdentifier = row.getHeaders()[0];  //hack assumes group id/household id is the first element. Added to handle dynamic group id header values.
        String groupId = row.get(groupIdIdentifier);
        String memberId = row.get(GroupMemberHeaders.memberId);

        Individual existingGroup;
        Individual existingMember;

        if (groupId == null || groupId.isEmpty()) {
            errorMsgs.add(String.format("'%s' is mandatory", groupIdIdentifier));
            return null;
        }
        if (memberId == null || memberId.isEmpty()) {
            errorMsgs.add(String.format("'%s' is mandatory", GroupMemberHeaders.memberId));
            return null;
        }

        existingGroup = individualRepository.findByLegacyIdOrUuid(groupId);
        if (existingGroup == null) {
            errorMsgs.add(String.format("Could not find '%s' - '%s'", groupIdIdentifier, groupId));
            return null;
        }
        existingMember = individualRepository.findByLegacyIdOrUuid(memberId);
        if (existingMember == null) {
            errorMsgs.add(String.format("Could not find '%s' - '%s'", GroupMemberHeaders.memberId, memberId));
            return null;
        }
        return createNewGroupSubject(existingGroup, existingMember, errorMsgs);
    }

    private GroupSubject createNewGroupSubject(Individual group, Individual member, List<String> errorMsgs) {
        if (!group.getSubjectType().isGroup()) {
            errorMsgs.add(String.format("Group Id '%s' is not of type group", group.getLegacyId()));
            return null;
        }
        GroupSubject groupSubject = new GroupSubject();

        groupSubject.setGroupSubject(group);
        groupSubject.setMemberSubject(member);
        return groupSubject;
    }

    private GroupRole getGroupRole(String role, List<String> errorMsgs, String roleIdentifier, Long groupSubjectTypeId) {
        if (role == null || role.isEmpty()) {
            errorMsgs.add(String.format("'%s' field is required", roleIdentifier));
            return null;
        }
        GroupRole groupRole = groupRoleRepository.findByRoleAndGroupSubjectTypeIdAndIsVoidedFalse(role, groupSubjectTypeId);
        if (groupRole == null) { // || groupRole.isVoided()) {
            errorMsgs.add(String.format("'%s' role not found", role));
            return null;
        }
        return groupRole;
    }

    private GroupRole getHouseholdGroupRole(Boolean isHeadOfHousehold, List<String> errorMsgs, String isHeadOfHouseholdIdentifier, Long groupSubjectTypeId) {
        if (isHeadOfHousehold == null) {
            errorMsgs.add(String.format("'%s' field is required", isHeadOfHouseholdIdentifier));
            return null;
        }

        String roleIdentifier = isHeadOfHousehold ? "Head of household" : "Member";
        return groupRoleRepository.findByRoleAndGroupSubjectTypeIdAndIsVoidedFalse(roleIdentifier, groupSubjectTypeId);
    }

    private IndividualRelation getRelationWithHeadOfHousehold(String relationshipWithHeadOfHousehold, List<String> errorMsgs) {
        if (relationshipWithHeadOfHousehold == null || relationshipWithHeadOfHousehold.isEmpty()) {
            errorMsgs.add(String.format("'%s' is mandatory for household members", HouseholdMemberHeaders.relationshipWithHeadOfHousehold));
            return null;
        }
        IndividualRelation individualRelation = individualRelationRepository.findByNameIgnoreCase(relationshipWithHeadOfHousehold);
        if (individualRelation == null || individualRelation.isVoided()) {
            errorMsgs.add(String.format("Invalid relation to head of household '%s'", relationshipWithHeadOfHousehold));
            return null;
        }
        return individualRelation;
    }

    private void saveRelationshipWithHeadOfHousehold(IndividualRelationship individualRelationship) {
        if (individualRelationship != null) {
            List<IndividualRelationship> existingRelationships = individualRelationshipRepository.findByIndividualaAndIndividualBAndIsVoidedFalse(individualRelationship.getIndividuala(), individualRelationship.getIndividualB());
            existingRelationships.forEach(existingRelationship -> existingRelationship.setVoided(true));

            individualRelationship.assignUUIDIfRequired();
            individualRelationshipRepository.save(individualRelationship);
        }
    }

    private void validateMembershipDates(LocalDate membershipStartDate, LocalDate membershipEndDate, List<String> errorMsgs) {
        if (membershipEndDate == null) {
            return;
        }
        if (membershipStartDate == null) {
            errorMsgs.add(String.format("%s is required when %s is given", GroupMemberHeaders.membershipStartDate, GroupMemberHeaders.membershipEndDate));
        } else if (membershipEndDate.isBefore(membershipStartDate)) {
            errorMsgs.add(String.format("'%s' %s is before '%s' %s", GroupMemberHeaders.membershipEndDate, membershipEndDate, GroupMemberHeaders.membershipStartDate, membershipStartDate));
        }
        if (membershipEndDate.isAfter(LocalDate.now(DateTimeUtil.IST))) {
            errorMsgs.add(String.format("'%s' %s cannot be in future", GroupMemberHeaders.membershipEndDate, membershipEndDate));
        }
    }

    private GroupSubject findMatchingGroupSubject(List<GroupSubject> existingGroupSubjects, LocalDate membershipStartDate, LocalDate membershipEndDate, List<String> errorMsgs) {
        List<GroupSubject> activeGroupSubjects = existingGroupSubjects.stream().filter(groupSubject -> !groupSubject.isVoided()).toList();
        List<GroupSubject> endedGroupSubjects = existingGroupSubjects.stream().filter(groupSubject -> groupSubject.isVoided() && groupSubject.getMembershipEndDate() != null).toList();
        if (activeGroupSubjects.size() > 1) {
            errorMsgs.add("Member has more than one active membership in this group. Remove the extra ones in the app, then upload again.");
            return null;
        }
        GroupSubject activeGroupSubject = activeGroupSubjects.isEmpty() ? null : activeGroupSubjects.get(0);

        if (membershipEndDate != null) {
            GroupSubject sameEndedGroupSubject = endedGroupSubjects.stream()
                    .filter(groupSubject -> membershipStartDate.equals(toIndianDate(groupSubject.getMembershipStartDate())) && membershipEndDate.equals(toIndianDate(groupSubject.getMembershipEndDate())))
                    .findFirst().orElse(null);
            if (sameEndedGroupSubject != null) {
                return sameEndedGroupSubject;
            }
            if (activeGroupSubject != null && membershipStartDate.equals(toIndianDate(activeGroupSubject.getMembershipStartDate())) && membershipEndDate.isAfter(membershipStartDate)) {
                return activeGroupSubject;
            }
            GroupSubject overlappingGroupSubject = endedGroupSubjects.stream()
                    .filter(groupSubject -> toIndianDate(groupSubject.getMembershipStartDate()).isBefore(membershipEndDate) && membershipStartDate.isBefore(toIndianDate(groupSubject.getMembershipEndDate())))
                    .findFirst().orElse(null);
            if (overlappingGroupSubject != null) {
                errorMsgs.add(overlapsEndedMembership(overlappingGroupSubject));
                return null;
            }
            if (activeGroupSubject != null && toIndianDate(activeGroupSubject.getMembershipStartDate()).isBefore(membershipEndDate)) {
                errorMsgs.add(String.format("Member is active in this group from %s. Give that start date to end it.", toIndianDate(activeGroupSubject.getMembershipStartDate())));
            }
            return null;
        }

        if (activeGroupSubject != null) {
            if (membershipStartDate != null && membershipStartDate.equals(toIndianDate(activeGroupSubject.getMembershipStartDate()))) {
                return activeGroupSubject;
            }
            errorMsgs.add("Member is already an active member of this group");
            return null;
        }
        LocalDate newMembershipStartDate = membershipStartDate != null ? membershipStartDate : LocalDate.now(DateTimeUtil.IST);
        endedGroupSubjects.stream()
                .filter(groupSubject -> toIndianDate(groupSubject.getMembershipEndDate()).isAfter(newMembershipStartDate))
                .findFirst()
                .ifPresent(groupSubject -> errorMsgs.add(overlapsEndedMembership(groupSubject)));
        return null;
    }

    private String overlapsEndedMembership(GroupSubject groupSubject) {
        return String.format("Overlaps an ended membership from %s to %s", toIndianDate(groupSubject.getMembershipStartDate()), toIndianDate(groupSubject.getMembershipEndDate()));
    }

    private LocalDate toIndianDate(DateTime dateTime) {
        return dateTime.withZone(DateTimeUtil.IST).toLocalDate();
    }

    private void endMembership(GroupSubject groupSubject, LocalDate membershipEndDate, String removalReasonConceptUUID) {
        groupSubject.setMembershipEndDate(membershipEndDate.toDateTimeAtStartOfDay());
        groupSubject.setRemovalReasonConceptUUID(removalReasonConceptUUID);
        groupSubject.setVoided(true);
    }

    private String getRemovalReasonConceptUUID(SubjectType groupSubjectType, String removalReason, boolean hasRemovalReason, List<String> errorMsgs) {
        JsonObject settings = groupSubjectType.getSettings();
        Object removalReasonConceptUuid = settings == null ? null : settings.get(String.valueOf(SubjectTypeSettingKey.removalReasonConceptUuid));
        Concept removalReasonConcept = removalReasonConceptUuid == null ? null : conceptRepository.findByUuid(removalReasonConceptUuid.toString());
        if (removalReasonConcept == null) {
            if (hasRemovalReason) {
                errorMsgs.add(String.format("%s is not asked for this group type", GroupMemberHeaders.removalReason));
            }
            return null;
        }
        if (!hasRemovalReason) {
            errorMsgs.add(String.format("%s is required for this group type", GroupMemberHeaders.removalReason));
            return null;
        }
        List<Concept> allowedRemovalReasons = removalReasonConcept.getConceptAnswers().stream()
                .filter(conceptAnswer -> !conceptAnswer.isVoided() && !conceptAnswer.getAnswerConcept().isVoided())
                .sorted(Comparator.comparingDouble(ConceptAnswer::getOrder).thenComparing(conceptAnswer -> conceptAnswer.getAnswerConcept().getName()))
                .map(ConceptAnswer::getAnswerConcept)
                .toList();
        Concept chosenRemovalReason = allowedRemovalReasons.stream().filter(concept -> concept.getName().equalsIgnoreCase(removalReason)).findFirst().orElse(null);
        if (chosenRemovalReason == null) {
            errorMsgs.add(String.format("%s '%s' is not one of: %s", GroupMemberHeaders.removalReason, removalReason, allowedRemovalReasons.stream().map(Concept::getName).collect(Collectors.joining(", "))));
            return null;
        }
        return chosenRemovalReason.getUuid();
    }

}
