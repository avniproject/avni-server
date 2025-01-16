package org.avni.server.domain.metabase;

public enum DatasetColumn {
    NAME(1),
    TYPE(2),
    SCHEMA_NAME(3);

    private final int index;

    DatasetColumn(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }
}