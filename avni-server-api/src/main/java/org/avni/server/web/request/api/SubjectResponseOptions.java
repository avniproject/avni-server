package org.avni.server.web.request.api;

public class SubjectResponseOptions {
    private final boolean includeSubjectType;
    private final boolean includeCatchments;
    private final boolean includeLocationProperties;

    private SubjectResponseOptions(boolean includeSubjectType, Boolean includeCatchments, boolean includeLocationProperties) {
        this.includeSubjectType = includeSubjectType;
        this.includeCatchments = includeCatchments != null && includeCatchments;
        this.includeLocationProperties = includeLocationProperties;
    }

    public static SubjectResponseOptions forSubjectList(boolean includeSubjectType, Boolean includeCatchments) {
        return new SubjectResponseOptions(includeSubjectType, includeCatchments, false);
    }

    public static SubjectResponseOptions forSingleSubject(Boolean includeCatchments) {
        return new SubjectResponseOptions(true, includeCatchments, false);
    }

    public static SubjectResponseOptions forSubjectUpdate(Boolean includeCatchments) {
        return new SubjectResponseOptions(true, includeCatchments, false);
    }

    public boolean isIncludeSubjectType() {
        return includeSubjectType;
    }

    public boolean isIncludeCatchments() {
        return includeCatchments;
    }

    public static SubjectResponseOptions forSingleSubjectWithLocationProperties(Boolean includeCatchments) {
        return new SubjectResponseOptions(true, includeCatchments, true);
    }

    public boolean isIncludeLocationProperties() {
        return includeLocationProperties;
    }
}
