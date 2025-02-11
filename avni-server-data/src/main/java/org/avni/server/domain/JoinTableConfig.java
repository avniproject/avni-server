package org.avni.server.domain;

import org.avni.server.domain.metabase.FieldDetails;
import org.avni.server.domain.metabase.TableDetails;

public class JoinTableConfig {
    TableDetails joinTargetTable;
    FieldDetails originField;
    FieldDetails destinationField;
    TableDetails alternateJoinSourceTable; //Optional: to be made use of when we want to join with table other than primaryTable

    public JoinTableConfig(TableDetails joinTargetTable, FieldDetails originField, FieldDetails destinationField) {
        this.joinTargetTable = joinTargetTable;
        this.originField = originField;
        this.destinationField = destinationField;
    }

    public JoinTableConfig(TableDetails joinTargetTable, FieldDetails originField, FieldDetails destinationField, TableDetails alternateJoinSourceTable) {
        this.joinTargetTable = joinTargetTable;
        this.originField = originField;
        this.destinationField = destinationField;
        this.alternateJoinSourceTable = alternateJoinSourceTable;
    }

    public TableDetails getJoinTargetTable() {
        return joinTargetTable;
    }

    public void setJoinTargetTable(TableDetails joinTargetTable) {
        this.joinTargetTable = joinTargetTable;
    }

    public FieldDetails getOriginField() {
        return originField;
    }

    public void setOriginField(FieldDetails originField) {
        this.originField = originField;
    }

    public FieldDetails getDestinationField() {
        return destinationField;
    }

    public void setDestinationField(FieldDetails destinationField) {
        this.destinationField = destinationField;
    }

    public TableDetails getAlternateJoinSourceTable() {
        return alternateJoinSourceTable;
    }

    public void setAlternateJoinSourceTable(TableDetails alternateJoinSourceTable) {
        this.alternateJoinSourceTable = alternateJoinSourceTable;
    }
}
