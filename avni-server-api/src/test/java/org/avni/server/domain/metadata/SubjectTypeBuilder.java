package org.avni.server.domain.metadata;

import org.avni.server.application.Format;
import org.avni.server.application.Subject;
import org.avni.server.domain.DeclarativeRule;
import org.avni.server.domain.GroupRole;
import org.avni.server.domain.SubjectType;

import java.util.Set;
import java.util.UUID;

public class SubjectTypeBuilder {
    private SubjectType subjectType;

    public SubjectTypeBuilder() {
        subjectType = new SubjectType();
    }

    public SubjectTypeBuilder(SubjectType subjectType) {
        this.subjectType = subjectType;
    }

    public SubjectTypeBuilder setUuid(String uuid) {
        subjectType.setUuid(uuid);
        return this;
    }

    public SubjectTypeBuilder setName(String name) {
        subjectType.setName(name);
        return this;
    }

    public SubjectTypeBuilder setMandatoryFieldsForNewEntity() {
        String s = UUID.randomUUID().toString();
        return setName(s).setUuid(s).setShouldSyncByLocation(true).setType(Subject.Individual).setActive(true);
    }

    public SubjectTypeBuilder setType(Subject type) {
        subjectType.setType(type);
    	return this;
    }

    public SubjectTypeBuilder setGroupRoles(Set<GroupRole> groupRoles) {
        subjectType.setGroupRoles(groupRoles);
        return this;
    }

    public SubjectTypeBuilder setHousehold(boolean household) {
        subjectType.setHousehold(household);
        return this;
    }

    public SubjectTypeBuilder setGroup(boolean group) {
        subjectType.setGroup(group);
        return this;
    }

    public SubjectTypeBuilder setSubjectSummaryRule(String subjectSummaryRule) {
        subjectType.setSubjectSummaryRule(subjectSummaryRule);
        return this;
    }

    public SubjectTypeBuilder setActive(Boolean active) {
        subjectType.setActive(active);
        return this;
    }

    public SubjectTypeBuilder setAllowEmptyLocation(boolean allowEmptyLocation) {
        subjectType.setAllowEmptyLocation(allowEmptyLocation);
        return this;
    }

    public SubjectTypeBuilder setAllowMiddleName(boolean allowMiddleName) {
        subjectType.setAllowMiddleName(allowMiddleName);
        return this;
    }

    public SubjectTypeBuilder setLastNameOptional(boolean lastNameOptional) {
        subjectType.setLastNameOptional(lastNameOptional);
        return this;
    }

    public SubjectTypeBuilder setAllowProfilePicture(boolean allowProfilePicture) {
        subjectType.setAllowProfilePicture(allowProfilePicture);
        return this;
    }

    public SubjectTypeBuilder setUniqueName(boolean uniqueName) {
        subjectType.setUniqueName(uniqueName);
        return this;
    }

    public SubjectTypeBuilder setValidFirstNameFormat(Format validFirstNameFormat) {
        subjectType.setValidFirstNameFormat(validFirstNameFormat);
        return this;
    }

    public SubjectTypeBuilder setValidMiddleNameFormat(Format validMiddleNameFormat) {
        subjectType.setValidMiddleNameFormat(validMiddleNameFormat);
        return this;
    }

    public SubjectTypeBuilder setValidLastNameFormat(Format validLastNameFormat) {
        subjectType.setValidLastNameFormat(validLastNameFormat);
        return this;
    }

    public SubjectTypeBuilder setIconFileS3Key(String iconFileS3Key) {
        subjectType.setIconFileS3Key(iconFileS3Key);
        return this;
    }

    public SubjectTypeBuilder setDirectlyAssignable(boolean directlyAssignable) {
        subjectType.setDirectlyAssignable(directlyAssignable);
        return this;
    }

    public SubjectTypeBuilder setShouldSyncByLocation(boolean shouldSyncByLocation) {
        subjectType.setShouldSyncByLocation(shouldSyncByLocation);
        return this;
    }

    public SubjectTypeBuilder setSyncRegistrationConcept2(String syncRegistrationConcept2) {
        subjectType.setSyncRegistrationConcept2(syncRegistrationConcept2);
        return this;
    }

    public SubjectTypeBuilder setSyncRegistrationConcept1Usable(Boolean syncRegistrationConcept1Usable) {
        subjectType.setSyncRegistrationConcept1Usable(syncRegistrationConcept1Usable);
        return this;
    }

    public SubjectTypeBuilder setSyncRegistrationConcept2Usable(Boolean syncRegistrationConcept2Usable) {
        subjectType.setSyncRegistrationConcept2Usable(syncRegistrationConcept2Usable);
        return this;
    }

    public SubjectTypeBuilder setNameHelpText(String nameHelpText) {
        subjectType.setNameHelpText(nameHelpText);
        return this;
    }

    public SubjectTypeBuilder setProgramEligibilityCheckRule(String programEligibilityCheckRule) {
        subjectType.setProgramEligibilityCheckRule(programEligibilityCheckRule);
        return this;
    }

    public SubjectTypeBuilder setProgramEligibilityCheckDeclarativeRule(DeclarativeRule programEligibilityCheckDeclarativeRule) {
        subjectType.setProgramEligibilityCheckDeclarativeRule(programEligibilityCheckDeclarativeRule);
        return this;
    }

    public SubjectTypeBuilder setSyncRegistrationConcept1(String syncRegistrationConcept1) {
        subjectType.setSyncRegistrationConcept1(syncRegistrationConcept1);
        return this;
    }

    public SubjectType build() {
        return subjectType;
    }
}
