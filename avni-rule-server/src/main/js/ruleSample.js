"use strict";
({params, imports}) => {
    const programEnrolment = params.entity;
    const decisions = params.decisions;
    console.log("imports", imports);
    console.log("params", params);
    const complicationsBuilder = new imports.rulesConfig.complicationsBuilder({
        programEnrolment: programEnrolment,
        complicationsConcept: "High Risk Conditions"
    });
    complicationsBuilder
        .addComplication("Gravida/pregnancies more than 5")
        .when.valueInEnrolment("Gravida")
        .is.greaterThan(5);
    complicationsBuilder
        .addComplication("Diabetes")
        .when.valueInEnrolment("Name of chronic disease")
        .containsAnswerConceptName("Diabetes");
    complicationsBuilder
        .addComplication("Hypertension")
        .when.valueInEnrolment("Name of chronic disease")
        .containsAnswerConceptName("Hypertension");
    complicationsBuilder
        .addComplication("TB")
        .when.valueInEnrolment("Name of chronic disease")
        .containsAnswerConceptName("TB");
    complicationsBuilder
        .addComplication("Heart disease")
        .when.valueInEnrolment("Name of chronic disease")
        .containsAnswerConceptName("Heart disease");
    complicationsBuilder
        .addComplication("Sickle cell disease")
        .when.valueInEnrolment("Name of chronic disease")
        .containsAnswerConceptName("Sickle cell disease");
    complicationsBuilder
        .addComplication("Leprosy")
        .when.valueInEnrolment("Name of chronic disease")
        .containsAnswerConceptName("Leprosy");
    decisions.enrolmentDecisions.push(complicationsBuilder.getComplications());
    return decisions;
};
