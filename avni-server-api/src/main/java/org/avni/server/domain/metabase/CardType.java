package org.avni.server.domain.metabase;

public enum CardType {
    QUESTION("question");

    private final String type;

    CardType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
