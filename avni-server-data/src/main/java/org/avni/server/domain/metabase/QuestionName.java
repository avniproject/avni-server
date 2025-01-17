package org.avni.server.domain.metabase;

public enum QuestionName {
    NonVoidedIndividual("Pie Chart : Count of Non Voided Individuals - Non Voided Subject Type", "individual",
            "subject_type",
            "subject_type_id",
            "name"),
    NonExitedNonVoidedProgram("Pie Chart : Number of Non exited and Non voided Enrolments for Non Voided Program", "program_enrolment",
            "program",
            "program_id",
            "name");

    private final String questionName;
    private final String primaryTableName;
    private final String secondaryTableName;
    private final String primaryField;
    private final String breakoutField;


    QuestionName(String questionName, String primaryTableName, String secondaryTableName, String primaryField, String breakoutField) {
        this.questionName = questionName;
        this.primaryTableName = primaryTableName;
        this.secondaryTableName = secondaryTableName;
        this.primaryField = primaryField;
        this.breakoutField = breakoutField;
    }

    public String getQuestionName() {
        return questionName;
    }

    public String getPrimaryTableName() {
        return primaryTableName;
    }

    public String getSecondaryTableName() {
        return secondaryTableName;
    }

    public String getPrimaryField() {
        return primaryField;
    }

    public String getBreakoutField() {
        return breakoutField;
    }
}
