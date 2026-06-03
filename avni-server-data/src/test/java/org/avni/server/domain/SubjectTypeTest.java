package org.avni.server.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubjectTypeTest {

    private SubjectType memberSubjectType(long id, boolean voided) {
        SubjectType memberSubjectType = new SubjectType();
        memberSubjectType.setId(id);
        memberSubjectType.setVoided(voided);
        return memberSubjectType;
    }

    private GroupRole groupRole(SubjectType groupSubjectType, SubjectType memberSubjectType, boolean voided) {
        GroupRole groupRole = new GroupRole();
        groupRole.setGroupSubjectType(groupSubjectType);
        groupRole.setMemberSubjectType(memberSubjectType);
        groupRole.setVoided(voided);
        return groupRole;
    }

    @Test
    void getMemberSubjectIdsReturnsEmptyForNonGroup() {
        SubjectType subjectType = new SubjectType();
        subjectType.setGroup(false);
        subjectType.setGroupRoles(new HashSet<>(Set.of(
                groupRole(subjectType, memberSubjectType(1L, false), false))));

        assertTrue(subjectType.getMemberSubjectIds().isEmpty());
    }

    @Test
    void getMemberSubjectIdsExcludesVoidedGroupRoles() {
        SubjectType group = new SubjectType();
        group.setGroup(true);

        SubjectType activeMember = memberSubjectType(1L, false);
        SubjectType removedMember = memberSubjectType(2L, false);
        SubjectType voidedMember = memberSubjectType(3L, true);

        group.setGroupRoles(new HashSet<>(Set.of(
                groupRole(group, activeMember, false),
                groupRole(group, removedMember, true),   // role voided when member removed from group
                groupRole(group, voidedMember, false))));

        assertEquals(Set.of(1L), new HashSet<>(group.getMemberSubjectIds()));
    }
}
