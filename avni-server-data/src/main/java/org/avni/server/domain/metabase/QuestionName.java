package org.avni.server.domain.metabase;

public enum QuestionName {
    NonVoidedIndividual("Registrations", "subject_view",
            "subject_type"),
    NonExitedNonVoidedProgram("Program Enrolments", "enrolment_view",
            "program_name");

    private final String questionName;
    private final String viewName;
    private final String breakoutField;


    QuestionName(String questionName, String primaryTableName, String breakoutField) {
        this.questionName = questionName;
        this.viewName = primaryTableName;
        this.breakoutField = breakoutField;
    }

    public String getQuestionName() {
        return questionName;
    }

    public String getViewName() {
        return viewName;
    }

    public String getBreakoutField() {
        return breakoutField;
    }
}
