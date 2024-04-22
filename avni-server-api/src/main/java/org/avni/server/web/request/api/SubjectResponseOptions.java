package org.avni.server.web.request.api;

public class SubjectResponseOptions {
    private final boolean includeSubjectType;
    private final boolean includeCatchments;

    private SubjectResponseOptions(boolean includeSubjectType, boolean includeCatchments) {
        this.includeSubjectType = includeSubjectType;
        this.includeCatchments = includeCatchments;
    }

    private SubjectResponseOptions(boolean includeSubjectType, Boolean includeCatchments) {
        this.includeSubjectType = includeSubjectType;
        this.includeCatchments = includeCatchments != null && includeCatchments;
    }

    public static SubjectResponseOptions forSubjectList(boolean includeSubjectType, Boolean includeCatchments) {
        return new SubjectResponseOptions(includeSubjectType, includeCatchments);
    }

    public static SubjectResponseOptions forSingleSubject(Boolean includeCatchments) {
        return new SubjectResponseOptions(true, includeCatchments);
    }

    public static SubjectResponseOptions forSubjectUpdate() {
        return new SubjectResponseOptions(true, true);
    }

    public boolean isIncludeSubjectType() {
        return includeSubjectType;
    }

    public boolean isIncludeCatchments() {
        return includeCatchments;
    }
}
