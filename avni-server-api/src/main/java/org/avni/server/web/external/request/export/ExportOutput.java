package org.avni.server.web.external.request.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.avni.server.exporter.v2.ExportV2ValidationHelper;
import org.springframework.http.ResponseEntity;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportOutput extends ExportEntityType {
    private List<ExportEntityType> encounters = new ArrayList<>();
    private List<ExportNestedOutput> groups = new ArrayList<>();
    private List<ExportNestedOutput> programs = new ArrayList<>();

    public List<ExportEntityType> getEncounters() {
        return encounters;
    }

    public void setEncounters(List<ExportEntityType> encounters) {
        this.encounters = encounters;
    }

    public List<ExportNestedOutput> getGroups() {
        return groups;
    }

    public void setGroups(List<ExportNestedOutput> groups) {
        this.groups = groups;
    }

    public List<ExportNestedOutput> getPrograms() {
        return programs;
    }

    public void setPrograms(List<ExportNestedOutput> programs) {
        this.programs = programs;
    }

    public ResponseEntity<?> validate() {
        List<String> errors = new ExportV2ValidationHelper().validate(this);
        if(!errors.isEmpty()) {
            return ResponseEntity.badRequest().body(errors);
        }
        return null;
    }

    public static class ExportNestedOutput extends ExportEntityType {
        private List<ExportEntityType> encounters = new ArrayList<>();

        public List<ExportEntityType> getEncounters() {
            return encounters;
        }

        public void setEncounters(List<ExportEntityType> encounters) {
            this.encounters = encounters;
        }

        @Override
        public List<ExportEntityType> getAllExportEntityTypes() {
            ArrayList<ExportEntityType> exportEntityTypes = new ArrayList<>();
            exportEntityTypes.add(this);
            exportEntityTypes.addAll(encounters);
            return exportEntityTypes;
        }
    }

    public void accept(ExportEntityTypeVisitor visitor) {
        visitor.visitSubject(this);
        encounters.forEach(encounter -> visitor.visitEncounter(encounter, this));
        groups.forEach(group -> {
            visitor.visitGroup(group);
            group.encounters.forEach(exportEntityType -> visitor.visitGroupEncounter(exportEntityType, group));
        });
        programs.forEach(program -> {
            visitor.visitProgram(program, this);
            program.encounters.forEach(exportEntityType -> visitor.visitProgramEncounter(exportEntityType, program, this));
        });
    }
}
