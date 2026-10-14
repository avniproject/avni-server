package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.domain.SubjectType;

import java.util.Arrays;

public class GroupMemberHeaders implements HeaderCreator {
    private final SubjectType memberSubjectType;
    public String groupId = "Group Id"; //default dummy value. Is set to the group name + Id in the sample.
    public final static String memberId = "Member Id";
    public final static String role = "Role";
    public final static String membershipStartDate = "Membership Start Date";
    public final static String membershipEndDate = "Membership End Date";
    public final static String removalReason = "Removal Reason";

    public GroupMemberHeaders(SubjectType memberSubjectType) {
        this.memberSubjectType = memberSubjectType;
    }

    @Override
    public String[] getAllHeaders() {
        return new String[]{groupId, memberId, role, membershipStartDate, membershipEndDate, removalReason};
    }

    @Override
    public String[] getAllHeaders(FormMapping formMapping, Object mode) {
        return getAllHeaders();
    }

    @Override
    public String[] getAllMandatoryHeaders(FormMapping formMapping, Object mode) {
        return Arrays.stream(getAllHeaders(formMapping, mode)).filter(header -> !header.equals(removalReason)).toArray(String[]::new);
    }

    @Override
    public String[] getConceptHeaders(FormMapping formMapping, String[] fileHeaders) {
        return new String[0];
    }

    @Override
    public String[] getAllDescriptions(FormMapping formMapping, Object mode) {
        return new String[0];
    }
}
