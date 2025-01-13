package org.avni.server.domain.metabase;

public enum QuestionName {
    QUESTION_1("Pie Chart : Count of Non Voided Individuals - Non Voided Subject Type"),
    QUESTION_2("Pie Chart : Number of Non exited and Non voided Enrollments for Non Voided Program");

    private final String questionName;

    QuestionName(String questionName) {
        this.questionName = questionName;
    }

    public String getQuestionName() {
        return questionName;
    }
}
