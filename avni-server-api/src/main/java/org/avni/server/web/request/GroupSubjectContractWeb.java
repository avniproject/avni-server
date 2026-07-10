package org.avni.server.web.request;

import org.avni.server.web.request.common.CommonIndividualRequest;
import org.joda.time.DateTime;

public class GroupSubjectContractWeb extends CHSRequest {
    IndividualContract group;
    IndividualContract member;
    GroupRoleContract role;
    EncounterMetadataContract encounterMetadata;
    private DateTime membershipStartDate;
    private DateTime membershipEndDate;

    public IndividualContract getGroup() {
        return group;
    }

    public void setGroup(IndividualContract group) {
        this.group = group;
    }

    public CommonIndividualRequest getMember() {
        return member;
    }

    public void setMember(IndividualContract member) {
        this.member = member;
    }

    public GroupRoleContract getRole() {
        return role;
    }

    public void setRole(GroupRoleContract role) {
        this.role = role;
    }

    public EncounterMetadataContract getEncounterMetadata() {
        return encounterMetadata;
    }

    public void setEncounterMetadata(EncounterMetadataContract encounterMetadata) {
        this.encounterMetadata = encounterMetadata;
    }

    public DateTime getMembershipStartDate() {
        return membershipStartDate;
    }

    public void setMembershipStartDate(DateTime membershipStartDate) {
        this.membershipStartDate = membershipStartDate;
    }

    public DateTime getMembershipEndDate() {
        return membershipEndDate;
    }

    public void setMembershipEndDate(DateTime membershipEndDate) {
        this.membershipEndDate = membershipEndDate;
    }
}
