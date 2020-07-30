package org.openchs.web.request;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class EnrolmentContract extends ReferenceDataContract{
    private String operationalProgramName;

    private DateTime enrolmentDateTime;

    private DateTime programExitDateTime;

    private String programUuid;

    private String subjectUuid;

    private Set<ProgramEncountersContract> programEncounters = new HashSet<>();

    private List<ObservationContract> observations = new ArrayList<>();
    private String programColor;

    public String getProgramColor() {
        return programColor;
    }

    public void setProgramColor(String programColor) {
        this.programColor = programColor;
    }

    public List<ObservationContract> getExitObservations() {
        return exitObservations;
    }

    public String getProgramUuid() {
		return programUuid;
	}

	public void setProgramUuid(String programUuid) {
		this.programUuid = programUuid;
	}

	public void setExitObservations(List<ObservationContract> exitObservations) {
        this.exitObservations = exitObservations;
    }

    private List<ObservationContract> exitObservations = new ArrayList<>();

    public List<ObservationContract> getObservations() {
        return observations;
    }

    public void setObservations(List<ObservationContract> observations) {
        this.observations = observations;
    }

    public Set<ProgramEncountersContract> getProgramEncounters() {
        return programEncounters;
    }

    public void setProgramEncounters(Set<ProgramEncountersContract> programEncounters) {
        this.programEncounters = programEncounters;
    }

    public String getOperationalProgramName() {
        return operationalProgramName;
    }

    public void setOperationalProgramName(String operationalProgramName) {
        this.operationalProgramName = operationalProgramName;
    }

    public DateTime getEnrolmentDateTime() {
        return enrolmentDateTime;
    }

    public void setEnrolmentDateTime(DateTime enrolmentDateTime) {
        this.enrolmentDateTime = enrolmentDateTime;
    }

    public DateTime getProgramExitDateTime() {
        return programExitDateTime;
    }

    public void setProgramExitDateTime(DateTime programExitDateTime) {
        this.programExitDateTime = programExitDateTime;
    }


    public String getSubjectUuid() {
        return subjectUuid;
    }

    public void setSubjectUuid(String subjectUuid) {
        this.subjectUuid = subjectUuid;
    }
}
